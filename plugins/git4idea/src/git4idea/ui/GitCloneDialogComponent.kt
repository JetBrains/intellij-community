// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui

import com.intellij.application.subscribe
import com.intellij.dvcs.ui.DvcsCloneDialogComponent
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.Alarm
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.config.*
import git4idea.remote.GitRememberedInputs
import java.nio.file.Paths

class GitCloneDialogComponent(project: Project, private val modalityState: ModalityState) :
  DvcsCloneDialogComponent(project,
                           GitUtil.DOT_GIT,
                           GitRememberedInputs.getInstance()) {

  private val executableManager get() = GitExecutableManager.getInstance()
  private val inlineComponent = GitExecutableInlineComponent(errorComponent, modalityState, mainPanel)
  private val errorNotifier = InlineErrorNotifier(inlineComponent, modalityState, this)

  private val executableProblemHandler = findGitExecutableProblemHandler(project)
  private val checkVersionAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private var listenerInstalled = false

  override fun doClone(project: Project, listener: CheckoutProvider.Listener) {
    val parent = Paths.get(getDirectory()).toAbsolutePath().parent

    val lfs = LocalFileSystem.getInstance()
    var destinationParent = lfs.findFileByIoFile(parent.toFile())
    if (destinationParent == null) {
      destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
    }
    if (destinationParent == null) {
      return
    }
    val sourceRepositoryURL = getUrl()
    val directoryName = Paths.get(getDirectory()).fileName.toString()
    val parentDirectory = parent.toAbsolutePath().toString()

    GitCheckoutProvider.clone(project, Git.getInstance(), listener, destinationParent, sourceRepositoryURL, directoryName, parentDirectory)
    val rememberedInputs = GitRememberedInputs.getInstance()
    rememberedInputs.addUrl(sourceRepositoryURL)
    rememberedInputs.cloneParentDir = parentDirectory
  }

  override fun onComponentSelected(dialogStateListener: VcsCloneDialogComponentStateListener) {
    dialogStateListener.onOkActionEnabled(false)

    val scheduleCheckVersion = {
      if (!errorNotifier.isTaskInProgress) {
        checkVersionAlarm.addRequest({ checkGitVersion(dialogStateListener) }, 0)
      }
    }

    if (!listenerInstalled) {
      listenerInstalled = true
      ApplicationActivationListener.TOPIC.subscribe(this, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          scheduleCheckVersion()
        }
      })
    }
    scheduleCheckVersion()
  }

  private fun checkGitVersion(dialogStateListener: VcsCloneDialogComponentStateListener) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showProgress("Checking Git version...")
    }

    try {
      executableManager.dropExecutableCache()
      val pathToGit = executableManager.pathToGit
      val gitVersion = executableManager.identifyVersion(pathToGit)

      invokeAndWaitIfNeeded(modalityState) {
        if (!gitVersion.isSupported) {
          showUnsupportedVersionError(project, gitVersion, errorNotifier)
          dialogStateListener.onOkActionEnabled(false)
        }
        else {
          inlineComponent.hideProgress()
          dialogStateListener.onOkActionEnabled(true)
        }
      }
    }
    catch (t: Throwable) {
      invokeAndWaitIfNeeded(modalityState) {
        executableProblemHandler.showError(t, errorNotifier, onErrorResolved = {
          dialogStateListener.onOkActionEnabled(true)
        })
        dialogStateListener.onOkActionEnabled(false)
      }
    }
  }
}
