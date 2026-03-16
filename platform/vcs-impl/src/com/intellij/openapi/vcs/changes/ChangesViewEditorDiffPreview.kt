// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.codeWithMe.ClientId.Companion.withExplicitClientId
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.actions.diff.ChangesViewShowDiffActionProvider
import com.intellij.openapi.vcs.changes.actions.diff.WrapperCombinedBlockProducer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.isToolWindowTabVertical
import com.intellij.openapi.vcs.changes.ui.CommitToolWindowUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.changes.viewModel.ChangesViewProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny
import javax.swing.JComponent

internal class ChangesViewEditorDiffPreview(
  val changesView: ChangesViewProxy,
  targetComponent: JComponent,
) : EditorTabDiffPreview(changesView.project) {
  private val cs = changesView.scope.childScope("ChangesViewEditorDiffPreview")

  init {
    PreviewOnNextDiffAction().registerCustomShortcutSet(targetComponent, this)

    cs.launch(Dispatchers.UiWithModelAccess) {
      changesView.diffRequests.collectLatest { (diffAction, clientId) ->
        withExplicitClientId(clientId) {
          when (diffAction) {
            ChangesViewDiffAction.SINGLE_CLICK_DIFF_PREVIEW -> {
              if (!isSplitterPreviewPresent()) {
                val opened = openPreview(false)
                if (!opened) closePreview()
              }
            }
            ChangesViewDiffAction.PERFORM_DIFF -> performDiffAction();
          }
        }
      }
    }
  }

  override fun hasContent(): Boolean = changesView.hasContentToDiff()

  override fun createViewer(): DiffEditorViewer = changesView.createDiffPreviewProcessor(true)

  override fun collectDiffProducers(selectedOnly: Boolean) = changesView.getDiffRequestProducers(selectedOnly)

  override fun handleEscapeKey() {
    closePreview()
    returnFocusToComponent()
  }

  private fun returnFocusToComponent() {
    ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.LOCAL_CHANGES)?.activate(null)
  }

  override fun openPreview(requestFocus: Boolean): Boolean =
    CommitToolWindowUtil.openDiff(ChangesViewContentManager.LOCAL_CHANGES, this, requestFocus)

  override fun updateDiffAction(event: AnActionEvent) {
    ChangesViewShowDiffActionProvider.updateAvailability(event)
  }

  @CalledInAny
  override fun getEditorTabName(processor: DiffEditorViewer?): String? {
    val wrapper = when (processor) {
      is ChangeViewDiffRequestProcessor -> processor.currentChange
      is CombinedDiffComponentProcessor -> (processor.currentBlock as? WrapperCombinedBlockProducer)?.wrapper
      else -> null
    }
    return if (wrapper != null) message("commit.editor.diff.preview.title", wrapper.presentableName)
    else message("commit.editor.diff.preview.empty.title")
  }

  override fun dispose() {
    super.dispose()
    cs.cancel()
  }

  private fun isSplitterPreviewPresent() =
    ChangesViewContentManager.shouldHaveSplitterDiffPreview(project, isToolWindowTabVertical(project, LOCAL_CHANGES))

  private inner class PreviewOnNextDiffAction : DumbAwareAction() {
    init {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      openPreview(true)
    }
  }
}
