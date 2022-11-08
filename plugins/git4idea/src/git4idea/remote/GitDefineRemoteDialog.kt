// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import git4idea.GitUtil.mention
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import git4idea.validators.GitRefNameValidator
import kotlinx.coroutines.*
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

private val LOG = logger<GitDefineRemoteDialog>()

class GitDefineRemoteDialog(
  private val repository: GitRepository,
  private val git: Git,
  @NlsSafe private val initialName: String,
  @NlsSafe initialUrl: String
) : DialogWrapper(repository.project) {

  private val uiDispatcher get() = Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()
  private val scope = CoroutineScope(SupervisorJob()).also { Disposer.register(disposable) { it.cancel() } }

  private val nameField = JBTextField(initialName, 30)
  private val urlField = ExtendableTextField(initialUrl, 30)
  private val loadingExtension = ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }

  private var urlAccessError: ValidationInfo? = null

  init {
    title = message("remotes.define.remote") + mention(repository)
    init()
  }

  val remoteName: String get() = nameField.text.orEmpty().trim()
  val remoteUrl: String get() = urlField.text.orEmpty().trim()

  override fun getPreferredFocusedComponent(): JComponent =
    if (nameField.text.isNullOrEmpty()) nameField else urlField

  override fun createCenterPanel(): JComponent =
    panel {
      row(message("remotes.define.remote.name")) {
        cell(nameField)
          .align(AlignX.FILL)
          .validationOnApply { nameNotBlank() ?: nameWellFormed() ?: nameUnique() }
      }
      row(message("remotes.define.remote.url")) {
        cell(urlField)
          .align(AlignX.FILL)
          .validationOnApply { urlNotBlank() ?: urlAccessError }
          .applyToComponent { clearUrlAccessErrorOnTextChanged() }
      }
    }

  override fun doOKAction() {
    scope.launch(uiDispatcher + CoroutineName("Define Remote - checking url")) {
      setLoading(true)
      try {
        urlAccessError = checkUrlAccess()
      }
      finally {
        setLoading(false)
      }

      if (urlAccessError == null) {
        super.doOKAction()
      }
      else {
        IdeFocusManager.getGlobalInstance().requestFocus(urlField, true)
        startTrackingValidation()
      }
    }
  }

  private fun setLoading(isLoading: Boolean) {
    nameField.isEnabled = !isLoading

    urlField.apply { if (isLoading) addExtension(loadingExtension) else removeExtension(loadingExtension) }
    urlField.isEnabled = !isLoading

    isOKActionEnabled = !isLoading
  }

  private fun nameNotBlank(): ValidationInfo? =
    if (remoteName.isNotEmpty()) null
    else ValidationInfo(message("remotes.define.empty.remote.name.validation.message"), nameField)

  private fun nameWellFormed(): ValidationInfo? =
    if (GitRefNameValidator.getInstance().checkInput(remoteName)) null
    else ValidationInfo(message("remotes.define.invalid.remote.name.validation.message"), nameField)

  private fun nameUnique(): ValidationInfo? {
    val name = remoteName

    return if (name == initialName || repository.remotes.none { it.name == name }) null
    else ValidationInfo(message("remotes.define.duplicate.remote.name.validation.message", name), nameField)
  }

  private fun urlNotBlank(): ValidationInfo? =
    if (remoteUrl.isNotEmpty()) null
    else ValidationInfo(message("remotes.define.empty.remote.url.validation.message"), urlField)

  private fun JBTextField.clearUrlAccessErrorOnTextChanged() =
    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        urlAccessError = null
      }
    })

  private suspend fun checkUrlAccess(): ValidationInfo? {
    val url = remoteUrl
    val result = lsRemote(url)

    if (result.success()) return null

    LOG.warn("Invalid remote. Name: $remoteName, URL: $url, error: ${result.errorOutputAsJoinedString}")
    return ValidationInfo(result.errorOutputAsHtmlString, urlField).withOKEnabled()
  }

  private suspend fun lsRemote(url: String): GitCommandResult =
    withContext(Dispatchers.IO) {
      runUnderIndicator { git.lsRemote(repository.project, virtualToIoFile(repository.root), url) }
    }
}