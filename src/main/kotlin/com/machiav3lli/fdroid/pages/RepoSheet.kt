package com.machiav3lli.fdroid.pages

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.machiav3lli.fdroid.MainApplication
import com.machiav3lli.fdroid.R
import com.machiav3lli.fdroid.database.entity.Repository
import com.machiav3lli.fdroid.ui.activities.PrefsActivityX
import com.machiav3lli.fdroid.ui.components.ActionButton
import com.machiav3lli.fdroid.ui.components.BlockText
import com.machiav3lli.fdroid.ui.components.SelectChip
import com.machiav3lli.fdroid.ui.components.TitleText
import com.machiav3lli.fdroid.ui.compose.icons.Phosphor
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.Check
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.GearSix
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.TrashSimple
import com.machiav3lli.fdroid.ui.compose.icons.phosphor.X
import com.machiav3lli.fdroid.ui.compose.utils.blockBorder
import com.machiav3lli.fdroid.ui.dialog.ActionsDialogUI
import com.machiav3lli.fdroid.ui.dialog.BaseDialog
import com.machiav3lli.fdroid.utility.extension.text.nullIfEmpty
import com.machiav3lli.fdroid.utility.extension.text.pathCropped
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URL
import java.util.Date
import java.util.Locale

@Composable
fun RepoPage(
    repositoryId: Long,
    initEditMode: Boolean,
    onDismiss: () -> Unit,
    updateRepo: (Repository?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo by MainApplication.db.repositoryDao.getFlow(repositoryId)
        .collectAsState(initial = null)
    val appsCount by MainApplication.db.productDao.countForRepositoryFlow(repositoryId)
        .collectAsState(0)
    var editMode by remember { mutableStateOf(initEditMode) }
    val openDeleteDialog = remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    var addressFieldValue by remember(repo) {
        mutableStateOf(
            TextFieldValue(
                repo?.address.orEmpty(),
                TextRange(repo?.address.orEmpty().length),
            )
        )
    }
    var fingerprintFieldValue by remember(repo) {
        mutableStateOf(
            TextFieldValue(
                repo?.fingerprint.orEmpty(),
                TextRange(repo?.fingerprint.orEmpty().length),
            )
        )
    }
    var usernameFieldValue by remember(repo) {
        mutableStateOf(
            TextFieldValue(
                repo?.authenticationPair?.first.orEmpty(),
                TextRange(repo?.authenticationPair?.first.orEmpty().length),
            )
        )
    }
    var passwordFieldValue by remember(repo) {
        mutableStateOf(
            TextFieldValue(
                repo?.authenticationPair?.second.orEmpty(),
                TextRange(repo?.authenticationPair?.second.orEmpty().length),
            )
        )
    }

    val addressValidity = remember { mutableStateOf(false) }
    val fingerprintValidity = remember { mutableStateOf(false) }
    val usernameValidity = remember { mutableStateOf(false) }
    val passwordValidity = remember { mutableStateOf(false) }
    val validations =
        listOf(addressValidity, fingerprintValidity, usernameValidity, passwordValidity)

    SideEffect {
        if (editMode && repo?.address.isNullOrEmpty()) {
            val clipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboardManager.primaryClip
                ?.let { if (it.itemCount > 0) it else null }
                ?.getItemAt(0)?.text?.toString().orEmpty()
            val (addressText, fingerprintText) = try {
                val uri = Uri.parse(URL(text.replaceFirst("fdroidrepos:", "https:")).toString())
                val fingerprintText =
                    uri.getQueryParameter("fingerprint")?.uppercase()?.nullIfEmpty()
                        ?: uri.getQueryParameter("FINGERPRINT")?.uppercase()?.nullIfEmpty()
                Pair(
                    uri.buildUpon().path(uri.path?.pathCropped)
                        .query(null).fragment(null).build().toString(), fingerprintText
                )
            } catch (e: Exception) {
                Pair(null, null)
            }
            if (addressText != null)
                addressFieldValue = TextFieldValue(addressText, TextRange(addressText.length))
            if (fingerprintText != null)
                fingerprintFieldValue =
                    TextFieldValue(fingerprintText, TextRange(fingerprintText.length))
        }

        invalidateAddress(addressValidity, addressFieldValue.text)
        invalidateFingerprint(fingerprintValidity, fingerprintFieldValue.text)
        invalidateAuthentication(
            passwordValidity,
            usernameFieldValue.text,
            passwordFieldValue.text,
        )
        invalidateAuthentication(
            usernameValidity,
            usernameFieldValue.text,
            passwordFieldValue.text,
        )
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(
                        id = if (!editMode) R.string.delete
                        else R.string.cancel
                    ),
                    icon = if (!editMode) Phosphor.TrashSimple
                    else Phosphor.X,
                    positive = false
                ) {
                    if (!editMode)
                        openDeleteDialog.value = true
                    else {
                        editMode = false
                        addressFieldValue = TextFieldValue(
                            repo?.address.orEmpty(),
                            TextRange(repo?.address.orEmpty().length),
                        )
                        fingerprintFieldValue = TextFieldValue(
                            repo?.fingerprint.orEmpty(),
                            TextRange(repo?.fingerprint.orEmpty().length),
                        )
                        usernameFieldValue = TextFieldValue(
                            repo?.authenticationPair?.first.orEmpty(),
                            TextRange(repo?.authenticationPair?.first.orEmpty().length),
                        )
                        passwordFieldValue = TextFieldValue(
                            repo?.authenticationPair?.second.orEmpty(),
                            TextRange(repo?.authenticationPair?.second.orEmpty().length),
                        )
                    }
                }
                ActionButton(
                    text = stringResource(
                        id = if (!editMode) R.string.edit
                        else R.string.save
                    ),
                    icon = if (!editMode) Phosphor.GearSix
                    else Phosphor.Check,
                    modifier = Modifier.weight(1f),
                    positive = true,
                    enabled = !editMode || validations.all { it.value },
                    onClick = {
                        if (!editMode) editMode = true
                        else {
                            // TODO show readable error
                            updateRepo(repo?.apply {
                                address = addressFieldValue.text
                                fingerprint = fingerprintFieldValue.text.uppercase()
                                setAuthentication(
                                    usernameFieldValue.text,
                                    passwordFieldValue.text,
                                )
                            })
                            // TODO sync a new when is already active
                            editMode = false
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    bottom = paddingValues.calculateBottomPadding(),
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                )
                .blockBorder()
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            if ((repo?.updated ?: -1) > 0L && !editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.name),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(text = repo?.name)
                }
            }
            if (!editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.description),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(text = repo?.description?.replace("\n", " "))
                }
            }
            if ((repo?.updated ?: -1) > 0L && !editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.recently_updated),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(
                        text = if (repo != null && repo?.updated != null) {
                            val date = Date(repo?.updated ?: 0)
                            val format =
                                if (DateUtils.isToday(date.time)) DateUtils.FORMAT_SHOW_TIME else
                                    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
                            DateUtils.formatDateTime(context, date.time, format)
                        } else stringResource(R.string.unknown)
                    )
                }
            }
            if (!editMode && repo?.enabled == true &&
                (repo?.lastModified.orEmpty().isNotEmpty() ||
                        repo?.entityTag.orEmpty().isNotEmpty())
            ) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.number_of_applications),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BlockText(text = appsCount.toString())
                }
            }
            item {
                TitleText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.address),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(visible = !editMode) {
                    BlockText(text = repo?.address)
                }
                AnimatedVisibility(visible = editMode) {
                    Column {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = addressFieldValue,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            onValueChange = {
                                addressFieldValue = it
                                invalidateAddress(addressValidity, addressFieldValue.text)
                            }
                        )
                        if (repo?.mirrors?.isNotEmpty() == true) LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(repo?.mirrors ?: emptyList()) { text ->
                                SelectChip(
                                    text = text,
                                    checked = text == addressFieldValue.text,
                                ) {
                                    addressFieldValue = TextFieldValue(
                                        text = text,
                                        selection = TextRange(text.length)
                                    )
                                    invalidateAddress(addressValidity, addressFieldValue.text)
                                }
                            }
                        }
                    }
                }
            }
            item {
                TitleText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.fingerprint),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(visible = !editMode) {
                    BlockText(
                        text = if (
                            (repo?.updated ?: -1) > 0L
                            && repo?.fingerprint.isNullOrEmpty()
                        ) stringResource(id = R.string.repository_unsigned_DESC)
                        else repo?.fingerprint
                            ?.windowed(2, 2, false)
                            ?.joinToString(separator = " ") { it.uppercase(Locale.US) + " " },
                        color = if (
                            (repo?.updated ?: -1) > 0L
                            && repo?.fingerprint?.isEmpty() == true
                        ) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        monospace = true,
                    )
                }
                AnimatedVisibility(visible = editMode) {
                    OutlinedTextField(
                        // TODO accept only hex literals
                        modifier = Modifier.fillMaxWidth(),
                        value = fingerprintFieldValue,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        onValueChange = {
                            fingerprintFieldValue = it
                            invalidateFingerprint(fingerprintValidity, fingerprintFieldValue.text)
                        }
                    )
                }
            }
            if (editMode) {
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.username),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = usernameFieldValue,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        isError = usernameValidity.value,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        onValueChange = {
                            usernameFieldValue = it
                            invalidateAuthentication(
                                usernameValidity,
                                usernameFieldValue.text,
                                passwordFieldValue.text,
                            )
                        }
                    )
                }
                item {
                    TitleText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.password),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth(),
                        value = passwordFieldValue,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        isError = passwordValidity.value,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        onValueChange = {
                            passwordFieldValue = it
                            invalidateAuthentication(
                                passwordValidity,
                                usernameFieldValue.text,
                                passwordFieldValue.text,
                            )
                        }
                    )
                }
            }
        }
    }

    if (openDeleteDialog.value) {
        BaseDialog(openDialogCustom = openDeleteDialog) {
            ActionsDialogUI(
                titleText = stringResource(id = R.string.confirmation),
                messageText = "${repo?.name}: ${stringResource(id = R.string.delete_repository_DESC)}",
                openDialogCustom = openDeleteDialog,
                primaryText = stringResource(id = R.string.delete),
                primaryIcon = Phosphor.TrashSimple,
                primaryAction = {
                    scope.launch {
                        (context as PrefsActivityX).syncConnection
                            .binder?.deleteRepository(repositoryId)
                        onDismiss()
                    }
                },
            )
        }
    }
}


private fun invalidateAddress(
    validity: MutableState<Boolean>,
    address: String,
) {
    // TODO check if already used
    validity.value = normalizeAddress(address) != null
}


private fun invalidateFingerprint(validity: MutableState<Boolean>, fingerprint: String) {
    validity.value = fingerprint.isEmpty() || fingerprint.length == 64
}


private fun invalidateAuthentication(
    validity: MutableState<Boolean>,
    username: String,
    password: String,
) {
    val usernameInvalid = username.contains(':')
    val usernameEmpty = username.isEmpty() && password.isNotEmpty()
    val passwordEmpty = username.isNotEmpty() && password.isEmpty()
    validity.value = !(usernameInvalid || usernameEmpty || passwordEmpty)
}

private fun normalizeAddress(address: String): String? {
    val uri = try {
        val uri = URI(address)
        if (uri.isAbsolute) uri.normalize() else null
    } catch (e: Exception) {
        null
    }
    val path = uri?.path?.pathCropped
    return if (uri != null && path != null) {
        try {
            URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                path,
                uri.query,
                uri.fragment
            ).toString()
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }
}