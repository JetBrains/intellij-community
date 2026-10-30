// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManagerRefreshHelper
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.AppIcon
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.VcsDisposable
import javax.swing.JComponent
import javax.swing.JPanel

private const val DIMENSION_KEY = "Vcs.Commit.Window"

/**
 * Hosts the commit interface in a window of its own, for a project with no frame to put a tool window in.
 *
 * The window holds the tab the Commit tool window would hold, so the user gets that tool window undocked. It is the changes view, or
 * the Git staging area in its place. The welcome screen commits this way on a recent project that it loaded without ever opening.
 */
internal class CommitWindowHost(private val project: Project) : CommitUiHost {
  /**
   * The window, built on the first show and kept until the project goes away.
   *
   * It is kept because its tab registers itself on the commit interface, and the interface refuses a second registration. Hiding and
   * showing the same window also keeps the tree and its listeners as they were.
   */
  private var window: CommitWindow? = null

  /** Whether the commit mode changed under the window, which leaves it holding the tab of the mode before. */
  private var isWindowStale = false

  init {
    // The Commit tool window builds its tabs again when the commit mode changes, for example when the Git staging area is turned off.
    // This window holds one of those tabs, so the next show builds it again from the extension that applies by then. The window is kept
    // until that show, because taking it away takes the changes view with it, and a commit that is running still reads that view.
    val connection = project.messageBus.connect(VcsDisposable.getInstance(project))
    CommitModeManager.subscribeOnCommitModeChange(connection, CommitModeManager.CommitModeListener { isWindowStale = true })
  }

  /** Always ready: the window is built on demand, so there is nothing that can be missing the way a tool window can. */
  override fun prepareToShow(): Boolean = true

  override val hidesOnCommit: Boolean
    get() = true

  override fun show(onShown: () -> Unit) {
    showWindow(LOCAL_CHANGES) { onShown() }
  }

  /**
   * Brings up the window with the [tabName] tab and gives [onShown] the component of that tab.
   *
   * It does nothing when no such tab is offered, or when the window holds another tab.
   */
  fun showWindow(tabName: String, onShown: (JComponent) -> Unit) {
    if (isWindowStale) {
      isWindowStale = false
      window?.let { Disposer.dispose(it.frame) }
    }

    val shown = window
    if (shown == null) {
      val created = createWindow(tabName) ?: return
      window = created
      // FrameWrapper.show builds the window contents, so it runs once. A later show only makes the same window visible again.
      created.frame.show()
    }
    else {
      if (shown.content.tabName != tabName) return
      shown.frame.getFrame().isVisible = true
    }

    val window = window ?: return
    bringToFront(window)

    // The changes of a project that is not open are computed behind vcs.recent.project.commit, but nothing has asked for them yet. The
    // tool window asks when its tab is shown, and this window is the first thing to show one here.
    ChangeListManagerRefreshHelper.requestRefresh(project)

    onShown(window.content.component)
  }

  /**
   * Puts the window away.
   *
   * [isOnCommit] is not read. The tool window host restores what it found, and this window has nothing to restore: it belongs to the
   * commit alone, so it goes away whether or not the commit ran.
   */
  override fun hide(isOnCommit: Boolean) {
    window?.frame?.getFrame()?.isVisible = false
  }

  private fun bringToFront(window: CommitWindow) {
    val frame = window.frame.getFrame()
    // The commit is started while the popup it was chosen from is still being disposed. That disposal settles focus back on the window
    // the popup belonged to. Raising this one has to wait for that to finish, or the welcome screen ends up on top of it.
    ApplicationManager.getApplication().invokeLater({
                                                      if (!frame.isShowing) return@invokeLater
                                                      frame.toFront()
                                                      (frame as? IdeFrame)?.let { AppIcon.getInstance().requestFocus(it) }
                                                    }, ModalityState.any())
  }

  private fun createWindow(tabName: String): CommitWindow? {
    val tab = createCommitTab(project, tabName) ?: return null

    // The tree builds its model from change list events only. For a project open in a frame the view is there before its changes are
    // computed, so the events that follow fill it. Here the changes were computed before the view existed, so no event is coming. The
    // first build has to be asked for.
    ChangesViewManager.getInstance(project).scheduleRefresh()

    val content = CommitWindowContent(project, tab.component)
    MnemonicHelper.init(content)

    // No project is passed. FrameWrapper parents the window to the frame of the project it is given, and this project has none. That is
    // the whole reason the interface is hosted here. With no project FrameWrapper parents the window to the active window instead, which
    // is the welcome screen the commit was started from. The window takes the project its actions need from its content, and
    // VcsDisposable takes the window away with the project.
    val frame = FrameWrapper(null, DIMENSION_KEY, false, VcsBundle.message("commit.window.title", project.name),
                             content).apply {
      Disposer.register(this, tab)
      Disposer.register(VcsDisposable.getInstance(project), this)
      // The window is gone once it is disposed from anywhere, so the next commit builds a new one instead of reusing a disposed wrapper.
      Disposer.register(this, Disposable { window = null })
      closeOnEsc()
      setOnCloseHandler {
        // Esc and the close button hide the window and end the commit. They do not dispose it, because it is reused.
        getFrame().isVisible = false
        ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler?.deactivate(false)
        false
      }
      preferredFocusedComponent = tab.preferredFocusableComponent
    }
    return CommitWindow(frame, tab)
  }
}

private class CommitWindow(@JvmField val frame: FrameWrapper, @JvmField val content: Content)

/**
 * Builds the [tabName] tab that the Commit tool window of [project] would show.
 *
 * The tool window fills its tabs from [ChangesViewContentEP]. The extension of the tab must go to the Commit tool window, and its own
 * predicate must accept the project. Returns null when no such tab is offered.
 */
private fun createCommitTab(project: Project, tabName: String): Content? {
  val ep = ChangesViewContentEP.EP_NAME.findFirstSafe(project) {
    it.tabName == tabName && it.isInCommitToolWindow && it.newPredicateInstance(project)?.test(project) != false
  } ?: return null
  val provider = ep.getInstance(project) ?: return null

  // The window carries its own title, so the tab keeps the name the extension gives it, and no name when it gives none.
  val content = ContentFactory.getInstance().createContent(JPanel(), ep.getDisplayName(project), false)
  // The tab name tells which tab the window holds.
  content.tabName = ep.tabName //NON-NLS
  provider.initTabContent(content)
  return content
}

/** Carries the project to the actions inside the window, which [FrameWrapper] supplies from its own project and this window has none. */
private class CommitWindowContent(private val project: Project, content: JComponent) : BorderLayoutPanel(), UiDataProvider {
  init {
    addToCenter(content)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.PROJECT] = project
  }
}
