// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui

import com.intellij.application.subscribe
import com.intellij.dvcs.ui.DvcsCloneDialogComponent
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.GitNotificationIdsHolder.Companion.CLONE_ERROR_UNABLE_TO_CREATE_DESTINATION_DIR
import git4idea.GitNotificationIdsHolder.Companion.CLONE_ERROR_UNABLE_TO_FIND_DESTINATION_DIR
import git4idea.GitUtil
import git4idea.checkout.GitCloneUtils
import git4idea.config.*
import git4idea.i18n.GitBundle
import git4idea.remote.GitRememberedInputs
import java.nio.file.Paths

class GitCloneDialogComponent(project: Project,
                              private val modalityState: ModalityState,
                              dialogStateListener: VcsCloneDialogComponentStateListener) :
  DvcsCloneDialogComponent(project,
                           GitUtil.DOT_GIT,
                           GitRememberedInputs.getInstance(),
                           dialogStateListener,
                           GitCloneDialogMainPanelCustomizer()) {
  private val LOG = Logger.getInstance(GitCloneDialogComponent::class.java)

  private val executableManager get() = GitExecutableManager.getInstance()
  private val inlineComponent = GitExecutableInlineComponent(errorComponent, modalityState, mainPanel)
  private val errorNotifier = InlineErrorNotifier(inlineComponent, modalityState, this)

  private val executableProblemHandler = findGitExecutableProblemHandler(project)
  private val checkVersionAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private var versionCheckState: VersionCheckState = VersionCheckState.NOT_CHECKED // accessed only on EDT

  override fun doClone(listener: CheckoutProvider.Listener) {
    val sourceRepositoryURL = getUrl()
    val directoryPath = getDirectory()
    GitCloneUtils.clone(project, sourceRepositoryURL, directoryPath,
                        (mainPanelCustomizer as GitCloneDialogMainPanelCustomizer).getShallowCloneOptions(), listener,
                        CLONE_ERROR_UNABLE_TO_CREATE_DESTINATION_DIR, CLONE_ERROR_UNABLE_TO_FIND_DESTINATION_DIR)

    val parent = Paths.get(directoryPath).toAbsolutePath().parent
    val parentDirectory = parent.toAbsolutePath().toString()

    val rememberedInputs = GitRememberedInputs.getInstance()
    rememberedInputs.addUrl(sourceRepositoryURL)
    rememberedInputs.cloneParentDir = parentDirectory
  }

  @RequiresEdt
  override fun onComponentSelected(dialogStateListener: VcsCloneDialogComponentStateListener) {
    updateOkActionState(dialogStateListener)

    if (versionCheckState == VersionCheckState.NOT_CHECKED) {
      versionCheckState = VersionCheckState.IN_PROGRESS
      scheduleCheckVersion(dialogStateListener)

      ApplicationActivationListener.TOPIC.subscribe(this, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          if (versionCheckState == VersionCheckState.FAILED) {
            scheduleCheckVersion(dialogStateListener)
          }
        }
      })
    }
  }

  private fun scheduleCheckVersion(dialogStateListener: VcsCloneDialogComponentStateListener) {
    if (!errorNotifier.isTaskInProgress) {
      checkVersionAlarm.addRequest({ checkGitVersion(dialogStateListener) }, 0)
    }
  }

  private fun checkGitVersion(dialogStateListener: VcsCloneDialogComponentStateListener) {
    invokeAndWaitIfNeeded(modalityState) {
      inlineComponent.showProgress(GitBundle.message("clone.dialog.checking.git.version"))
    }

    try {
      val executable = executableManager.getExecutable(null)
      val gitVersion = executableManager.identifyVersion(executable)

      invokeAndWaitIfNeeded(modalityState) {
        if (!gitVersion.isSupported) {
          showUnsupportedVersionError(project, gitVersion, errorNotifier)
        }
        else {
          inlineComponent.hideProgress()
        }
        versionCheckState = VersionCheckState.SUCCESS
        updateOkActionState(dialogStateListener)
      }
    }
    catch (t: Throwable) {
      invokeAndWaitIfNeeded(modalityState) {
        executableProblemHandler.showError(t, errorNotifier, onErrorResolved = {
          versionCheckState = VersionCheckState.SUCCESS
          updateOkActionState(dialogStateListener)
        })
        versionCheckState = VersionCheckState.FAILED
        updateOkActionState(dialogStateListener)
      }
    }
  }

  @RequiresEdt
  override fun isOkActionEnabled(): Boolean = super.isOkActionEnabled() && versionCheckState == VersionCheckState.SUCCESS

  private fun notifyCloneError(project: Project) {
    VcsNotifier.getInstance(project).notifyError(CLONE_ERROR_UNABLE_TO_CREATE_DESTINATION_DIR,
                                                 VcsBundle.message("clone.dialog.clone.button"),
                                                 VcsBundle.message("clone.dialog.unable.create.destination.error"))
  }

  private enum class VersionCheckState {
    NOT_CHECKED,
    SUCCESS,
    IN_PROGRESS,
    FAILED
  }
}

private class GitCloneDialogMainPanelCustomizer : DvcsCloneDialogComponent.MainPanelCustomizer() {
  private val vm = GitShallowCloneViewModel()

  override fun configure(panel: Panel) {
    GitShallowCloneComponentFactory.appendShallowCloneRow(panel, vm).bottomGap(BottomGap.SMALL)
  }

  fun getShallowCloneOptions() = vm.getShallowCloneOptions()
}