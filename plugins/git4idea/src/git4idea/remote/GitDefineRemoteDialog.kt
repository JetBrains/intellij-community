// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.remote

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.xml.util.XmlStringUtil.wrapInHtml
import git4idea.GitUtil.mention
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import git4idea.validators.GitRefNameValidator
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

private val LOG = logger<GitDefineRemoteDialog>()

class GitDefineRemoteDialog(
  private val repository: GitRepository,
  private val git: Git,
  @NlsSafe private val initialName: String,
  @NlsSafe initialUrl: String
) : DialogWrapper(repository.project) {

  private val nameField = JBTextField(initialName, 30)
  private val urlField = JBTextField(initialUrl, 30)

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
        nameField(growX).withValidationOnApply { nameNotBlank() ?: nameWellFormed() ?: nameUnique() }
      }
      row(message("remotes.define.remote.url")) {
        urlField(growX).withValidationOnApply { urlNotBlank() }
      }
    }

  override fun doOKAction() {
    val url = remoteUrl
    val error = validateRemoteUnderModal(url)
    
    if (error != null) {
      LOG.warn("Invalid remote. Name: $remoteName, URL: $url, error: $error")
      showErrorDialog(repository.project, wrapInHtml(error), message("remotes.define.invalid.remote"))
    }
    else {
      super.doOKAction()
    }
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

  @Nls
  private fun validateRemoteUnderModal(url: String): String? {
    val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      ThrowableComputable<GitCommandResult, RuntimeException> { git.lsRemote(repository.project, virtualToIoFile(repository.root), url) },
      message("remotes.define.checking.url.progress.message"),
      true,
      repository.project
    )

    return if (result.success()) null
    else message("remotes.define.remote.url.validation.fail.message") + " " + result.errorOutputAsHtmlString
  }
}