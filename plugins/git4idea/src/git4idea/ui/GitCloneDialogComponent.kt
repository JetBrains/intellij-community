// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.Alarm
import com.intellij.util.ui.AsyncProcessIcon
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.config.*
import git4idea.remote.GitRememberedInputs
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.SwingConstants

class GitCloneDialogComponent(project: Project, private val modalityState: ModalityState) :
  DvcsCloneDialogComponent(project,
                           GitUtil.DOT_GIT,
                           GitRememberedInputs.getInstance()) {

  private val executableManager get() = GitExecutableManager.getInstance()
  private val inlineComponent = GitCloneDialogInlineComponent()
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
        executableProblemHandler.showError(t, errorNotifier)
        dialogStateListener.onOkActionEnabled(false)
      }
    }
  }

  inner class GitCloneDialogInlineComponent : InlineComponent {
    private val busyIcon: AsyncProcessIcon = createBusyIcon()

    override fun showProgress(text: String) {
      errorComponent.removeAll()
      busyIcon.resume()

      val label = JBLabel(text).apply {
        foreground = JBColor.GRAY
      }

      errorComponent.addToLeft(busyIcon)
      errorComponent.addToCenter(label)
      mainPanel.validate()
    }

    override fun showError(errorText: String, link: LinkLabel<*>?) {
      busyIcon.suspend()
      errorComponent.removeAll()

      val label = multilineLabel(errorText).apply {
        foreground = JBColor.RED
      }

      errorComponent.addToCenter(label)
      if (link != null) {
        link.verticalAlignment = SwingConstants.TOP
        errorComponent.addToRight(link)
      }
      mainPanel.validate()
    }

    override fun showMessage(text: String) {
      busyIcon.suspend()
      errorComponent.removeAll()

      errorComponent.addToLeft(JBLabel(text))
      mainPanel.validate()
    }

    override fun hideProgress() {
      busyIcon.suspend()
      errorComponent.removeAll()

      mainPanel.validate()
    }

    private fun createBusyIcon(): AsyncProcessIcon = AsyncProcessIcon(toString()).apply {
      isOpaque = false
      setPaintPassiveIcon(false)
    }
  }
}

private fun multilineLabel(text: String): JComponent = JBLabel(text).apply {
  setAllowAutoWrapping(true)
  setCopyable(true)
}
