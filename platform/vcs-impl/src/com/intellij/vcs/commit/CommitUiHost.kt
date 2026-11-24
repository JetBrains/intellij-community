// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.isCommitHostedInWindow
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Shows the commit interface to the user and puts it away again.
 *
 * The interface lives in the Commit tool window, which hosts it for a project open in a frame. A project with no frame of its own has no
 * tool window, so it gets a host that shows the interface in a window of its own. See [isCommitHostedInWindow].
 */
internal sealed interface CommitUiHost {
  /**
   * Readies the host and remembers what to restore once the commit is done.
   *
   * Returns false when there is nowhere to show the interface. That leaves the commit to the modal dialog.
   *
   * It is separate from [show] because the interface makes itself visible between the two calls. It must be visible before the host
   * reveals it.
   */
  fun prepareToShow(): Boolean

  /** Brings the interface in front of the user and runs [onShown] once it is there. */
  fun show(onShown: () -> Unit)

  /** Puts the interface away. [isOnCommit] is true when the commit it was shown for has just run. */
  fun hide(isOnCommit: Boolean)

  /**
   * Whether the interface goes away once a commit starts.
   *
   * Such a host shows the interface only when the user starts a commit. A change of the commit mode does not show or hide it.
   */
  val hidesOnCommit: Boolean
    get() = false
}

/**
 * The host the commit interface of a project uses.
 *
 * It holds one host for the life of the project. The window host must not be built twice, because its panel registers itself on the
 * commit interface, and the interface refuses a second registration.
 */
@Service(Service.Level.PROJECT)
internal class CommitUiHostHolder(private val project: Project) {
  val current: CommitUiHost by lazy { if (project.isCommitHostedInWindow) CommitWindowHost(project) else CommitToolWindowHost(project) }

  companion object {
    fun getInstance(project: Project): CommitUiHostHolder = project.service()
  }
}

/** Hosts the commit interface in the Commit tool window, and leaves the tool window as it found it. */
private class CommitToolWindowHost(private val project: Project) : CommitUiHost {
  /** Whether the tool window was hidden when the interface was shown, and so has to be hidden again. Null while nothing is shown. */
  private var hideOnCommit: Boolean? = null

  override fun prepareToShow(): Boolean {
    val toolWindow = getVcsToolWindow() ?: return false
    // Only the first activation records it. Activating an interface that is already up must not forget the original state.
    if (hideOnCommit == null) {
      hideOnCommit = !toolWindow.isVisible
    }
    return true
  }

  override fun show(onShown: () -> Unit) {
    ChangesViewContentManager.getInstance(project).selectContent(LOCAL_CHANGES)
    getVcsToolWindow()?.activate(onShown, false)
  }

  override fun hide(isOnCommit: Boolean) {
    if (isOnCommit && hideOnCommit == true) {
      getVcsToolWindow()?.hide(null)
    }
    hideOnCommit = null
  }

  private fun getVcsToolWindow(): ToolWindow? = getToolWindowFor(project, LOCAL_CHANGES)
}

/** Shows the tabs of the VCS tool windows to other modules. */
@ApiStatus.Internal
object VcsToolWindowTabs {
  /**
   * Shows the [tabName] tab the user commits from, and gives [onShown] the component of that tab.
   *
   * [tabName] names the changes view, or the Git staging area in its place. A project with no frame of its own has no tool window. It
   * shows the same tab in a window instead.
   */
  fun showCommitTab(project: Project, tabName: String, onShown: (JComponent) -> Unit) {
    when (val host = CommitUiHostHolder.getInstance(project).current) {
      is CommitWindowHost -> host.showWindow(tabName, onShown)
      is CommitToolWindowHost -> showTab(project, tabName, onShown)
    }
  }

  /**
   * Puts away the tab the user commits from, once a commit from it starts.
   *
   * Only a window goes away, because it belongs to the commit alone. A tool window stays as it is.
   */
  fun hideCommitTabOnCommit(project: Project) {
    val host = CommitUiHostHolder.getInstance(project).current
    if (host.hidesOnCommit) host.hide(isOnCommit = true)
  }

  /**
   * Activates the tool window that holds the [tabName] tab, selects the tab, and gives [onShown] the component of that tab.
   *
   * It does nothing when the project has no such tool window.
   */
  fun showTab(project: Project, tabName: String, onShown: (JComponent) -> Unit) {
    ToolWindowManager.getInstance(project).invokeLater {
      val toolWindow = getToolWindowFor(project, tabName) ?: return@invokeLater
      toolWindow.activate({
                            val contentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager
                            val content = contentManager.findContent(tabName) ?: return@activate

                            contentManager.setSelectedContent(content, true)
                            onShown(content.component)
                          }, true)
    }
  }
}
