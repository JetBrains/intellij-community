// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkout

import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.commands.Git
import git4idea.commands.GitShallowCloneOptions
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Paths

@ApiStatus.Internal
object GitCloneUtils {
  @RequiresEdt
  fun clone(
    project: Project,
    selectedUrl: String,
    directoryPath: String,
    shallowCloneOptions: GitShallowCloneOptions?,
    checkoutListener: CheckoutProvider.Listener,
    @NotificationTitle unableToCreateDirectoryId: String,
    @NotificationTitle unableToFindDirectoryId: String,
  ) {
    val parent = Paths.get(directoryPath).toAbsolutePath().parent
    val destinationValidation = CloneDvcsValidationUtils.createDestination(parent.toString())
    if (destinationValidation != null) {
      notifyCreateDirectoryFailed(project, destinationValidation.message, unableToCreateDirectoryId)
      return
    }

    val lfs = StandardFileSystems.local()
    var destinationParent = lfs.findFileByPath(parent.toFile().absolutePath)
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByPath(parent.toFile().absolutePath)
    }
    if (destinationParent == null) {
      notifyDestinationNotFound(project, unableToFindDirectoryId)
      return
    }

    val directoryName = Paths.get(directoryPath).fileName.toString()
    val parentDirectory = parent.toAbsolutePath().toString()

    GitCheckoutProvider.clone(
      project,
      Git.getInstance(),
      checkoutListener,
      destinationParent,
      selectedUrl,
      directoryName,
      parentDirectory,
      shallowCloneOptions,
    )
  }

  private fun notifyCreateDirectoryFailed(project: Project, message: String, @NotificationTitle displayId: String) {
    thisLogger().error(VcsBundle.message("clone.dialog.unable.create.destination.error"), message)
    VcsNotifier.getInstance(project).notifyError(
      displayId,
      VcsBundle.message("clone.dialog.clone.failed.error"),
      GitBundle.message("clone.dialog.error.unable.to.find.destination.directory")
    )
  }

  private fun notifyDestinationNotFound(project: Project, @NotificationTitle displayId: String) {
    thisLogger().error(GitBundle.message("clone.dialog.error.destination.not.exist"))
    VcsNotifier.getInstance(project).notifyError(
      displayId,
      VcsBundle.message("clone.dialog.clone.failed.error"),
      GitBundle.message("clone.dialog.error.unable.to.find.destination.directory")
    )
  }
}