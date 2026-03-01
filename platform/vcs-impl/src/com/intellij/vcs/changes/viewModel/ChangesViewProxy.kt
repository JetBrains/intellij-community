// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.codeWithMe.ClientId
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ChangesViewDiffAction
import com.intellij.openapi.vcs.changes.ChangesViewSplitComponentBinding
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Backend-side Changes View accessor used in [com.intellij.openapi.vcs.changes.ChangesViewManager].
 *
 * In monolith mode [panel] is the instance of [CommitChangesViewWithToolbarPanel] that renders the local changes tree and toolbar.
 * In split mode it contains a placeholder produced via [ChangesViewSplitComponentBinding].
 */
// TODO IJPL-173924 cleanup methods returning tree/component
@ApiStatus.Internal
abstract class ChangesViewProxy(val project: Project, val scope: CoroutineScope) : Disposable {
  abstract val inclusionChanged: SharedFlow<Unit>

  abstract val diffRequests: SharedFlow<Pair<ChangesViewDiffAction, ClientId>>

  /**
   * [initPanel] should be called before [panel] is accessed
   */
  abstract val panel: JComponent

  abstract fun initPanel()

  abstract fun setToolbarHorizontal(horizontal: Boolean)

  abstract fun scheduleRefreshNow(callback: Runnable?)
  abstract fun scheduleDelayedRefresh()
  abstract fun setGrouping(groupingKey: String)
  abstract fun resetViewImmediatelyAndRefreshLater()

  abstract fun setInclusionModel(model: InclusionModel?)

  abstract fun getDisplayedChanges(): List<Change>
  abstract fun getIncludedChanges(): List<Change>
  abstract fun getDisplayedUnversionedFiles(): List<FilePath>
  abstract fun getIncludedUnversionedFiles(): List<FilePath>

  abstract fun expand(item: Any)
  abstract fun select(item: Any)
  abstract fun selectFirst(items: Collection<Any>)

  abstract fun selectFile(vFile: VirtualFile?)
  abstract fun selectChanges(changes: List<Change>)

  abstract fun hasContentToDiff(): Boolean
  abstract fun getDiffRequestProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer>?

  abstract fun createDiffPreviewProcessor(isInEditor: Boolean): ChangeViewDiffRequestProcessor

  fun getPreferredFocusedComponent(): JComponent {
    val p = panel
    return if (p is CommitChangesViewWithToolbarPanel) p.changesView.preferredFocusedComponent else p
  }

  override fun dispose() {
    scope.cancel()
  }

  companion object {
    @JvmStatic
    fun create(project: Project, parentScope: CoroutineScope): ChangesViewProxy {
      val scope = parentScope.childScope("ChangesViewProxy")
      val model =
        if (RdLocalChanges.isEnabled()) RpcChangesViewProxy(project, scope)
        else LocalChangesViewProxy(BackendCommitChangesViewWithToolbarPanel(LocalChangesListView(project), scope), scope)

      return model
    }
  }
}
