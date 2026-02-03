// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.diagnostic.Checks.fail
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.combined.COMBINED_DIFF_SCROLL_TO_BLOCK
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.diff.tools.combined.CombinedDiffManager
import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.WrapperCombinedBlockProducer
import com.intellij.openapi.vcs.changes.actions.diff.prepareCombinedBlocksFromWrappers
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JTree

open class TreeHandlerDiffRequestProcessor(
  place: String,
  protected val tree: ChangesTree,
  protected val handler: ChangesTreeDiffPreviewHandler
) : ChangeViewDiffRequestProcessor(tree.project, place) {

  final override fun iterateSelectedChanges(): Iterable<Wrapper> {
    return handler.iterateSelectedChanges(tree)
  }

  final override fun iterateAllChanges(): Iterable<Wrapper> {
    return handler.iterateAllChanges(tree)
  }

  final override fun selectChange(change: Wrapper) {
    handler.selectChange(tree, change)
  }

  override fun showAllChangesForEmptySelection(): Boolean {
    return handler.isShowAllChangesForEmptySelection
  }
}

open class TreeHandlerChangesTreeTracker(
  protected val tree: ChangesTree,
  protected val editorViewer: DiffEditorViewer,
  protected val handler: ChangesTreeDiffPreviewHandler,
  /**
   * If true, the viewer will be refreshed on 'addNotify' and while showing on screen
   * If false, the viewer will be kept up to date at all times
   */
  protected val updateWhileShown: Boolean = false
) {
  private val isCombinedViewer = editorViewer is CombinedDiffComponentProcessor
  private val isForceKeepCurrentFileWhileFocused = editorViewer is ChangeViewDiffRequestProcessor &&
                                                   editorViewer.forceKeepCurrentFileWhileFocused()

  private val updatePreviewQueue = MergingUpdateQueue("TreeHandlerChangesTreeTracker", 100, true, editorViewer.component, editorViewer.disposable).apply {
    setRestartTimerOnAdd(true)
  }

  init {
    assert(editorViewer is CombinedDiffComponentProcessor ||
           editorViewer is ChangeViewDiffRequestProcessor)
  }

  open fun track() {
    val disposable = editorViewer.disposable

    tree.addSelectionListener(Runnable {
      if (tree.isModelUpdateInProgress) {
        updatePreviewLater(UpdateType.ON_MODEL_CHANGE)
      }
      else {
        updatePreviewLater(UpdateType.ON_SELECTION_CHANGE)
      }
    }, disposable)

    val changeListener = PropertyChangeListener {
      updatePreviewLater(UpdateType.ON_MODEL_CHANGE)
    }
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, changeListener)
    Disposer.register(disposable) { tree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, changeListener) }

    if (isForceKeepCurrentFileWhileFocused) {
      val focusListener = object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          updatePreviewLater(UpdateType.ON_SELECTION_CHANGE)
        }
      }
      tree.addFocusListener(focusListener)
      Disposer.register(disposable) { tree.removeFocusListener(focusListener) }
    }

    if (updateWhileShown) {
      DiffUtil.installShowNotifyListener(editorViewer.component, object : Activatable {
        override fun showNotify() {
          updatePreview(UpdateType.FULL)
          updatePreviewQueue.cancelAllUpdates()
        }

        override fun hideNotify() {
          updatePreviewQueue.cancelAllUpdates()
        }
      })
    }
    else {
      updatePreview(UpdateType.FULL)
    }
  }

  fun updatePreviewLater(updateType: UpdateType) {
    updatePreviewQueue.queue(PreviewUpdate(updateType))
  }

  private fun updatePreview(updateType: UpdateType) {
    val state = !updateWhileShown || UIUtil.isShowing(editorViewer.component)
    if (state) {
      refreshPreview(updateType)
    }
    else {
      clearPreview()
    }
  }

  private fun refreshPreview(updateType: UpdateType) {
    if (editorViewer.disposable.isDisposed) return
    when (editorViewer) {
      is CombinedDiffComponentProcessor -> {
        val onlyBlockSelection = updateType == UpdateType.ON_SELECTION_CHANGE
        refreshCombinedDiffProcessor(tree, editorViewer, handler, onlyBlockSelection)
      }
      is ChangeViewDiffRequestProcessor -> {
        val fromModelRefresh = updateType == UpdateType.ON_MODEL_CHANGE
        editorViewer.refresh(fromModelRefresh)
      }
      else -> fail(editorViewer)
    }
  }

  private fun clearPreview() {
    if (editorViewer.disposable.isDisposed) return
    when (editorViewer) {
      is CombinedDiffComponentProcessor -> {
        editorViewer.cleanBlocks()
      }
      is ChangeViewDiffRequestProcessor -> {
        editorViewer.clear()
      }
      else -> fail(editorViewer)
    }
  }

  private inner class PreviewUpdate(val updateType: UpdateType) : Update(updateType) {
    override fun run() {
      updatePreview(updateType)
    }

    override fun canEat(eatenUpdate: Update): Boolean {
      if (eatenUpdate !is PreviewUpdate) return false
      if (updateType == eatenUpdate.updateType) return true
      if (updateType == UpdateType.FULL) return true
      if (isCombinedViewer) {
        return updateType == UpdateType.ON_MODEL_CHANGE &&
               eatenUpdate.updateType == UpdateType.ON_SELECTION_CHANGE
      }
      else if (isForceKeepCurrentFileWhileFocused) {
        return updateType == UpdateType.ON_SELECTION_CHANGE &&
               eatenUpdate.updateType == UpdateType.ON_MODEL_CHANGE
      }
      else {
        return true
      }
    }
  }

  enum class UpdateType {
    FULL, ON_SELECTION_CHANGE, ON_MODEL_CHANGE
  }
}


abstract class TreeHandlerEditorDiffPreview(
  protected val tree: ChangesTree,
  targetComponent: JComponent = tree,
  protected val handler: ChangesTreeDiffPreviewHandler
) : EditorTabDiffPreview(tree.project) {
  constructor(tree: ChangesTree, handler: ChangesTreeDiffPreviewHandler) : this(tree, tree, handler)

  init {
    tree.doubleClickHandler = Processor { e -> handleDoubleClick(e) }
    tree.enterKeyHandler = Processor { _ -> handleEnterKey() }
    tree.addSelectionListener { handleSingleClick() }
    PreviewOnNextDiffAction().registerCustomShortcutSet(targetComponent, this)

    UIUtil.putClientProperty(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
  }

  protected open fun isPreviewOnDoubleClick(): Boolean = true
  open fun handleDoubleClick(e: MouseEvent): Boolean {
    if (!isPreviewOnDoubleClick()) return false
    if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return false
    return performDiffAction()
  }

  protected open fun isPreviewOnEnter(): Boolean = true
  open fun handleEnterKey(): Boolean {
    if (!isPreviewOnEnter()) return false
    return performDiffAction()
  }

  protected open fun isOpenPreviewWithSingleClickEnabled(): Boolean = false
  protected open fun isOpenPreviewWithSingleClick(): Boolean {
    if (!isOpenPreviewWithSingleClickEnabled()) return false
    if (!VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN) return false
    return true
  }

  protected open fun handleSingleClick() {
    if (!isOpenPreviewWithSingleClick()) return
    val opened = openPreview(false)
    if (!opened) {
      closePreview() // auto-close editor tab if nothing to preview
    }
  }

  protected open fun isOpenPreviewWithNextDiffShortcut(): Boolean = true
  protected open fun handleNextDiffShortcut() {
    if (!isOpenPreviewWithNextDiffShortcut()) return
    openPreview(true)
  }

  override fun handleEscapeKey() {
    closePreview()
    getApplication().invokeLater {
      returnFocusToTree()
    }
  }

  protected open fun returnFocusToTree() = Unit

  final override fun hasContent(): Boolean {
    return handler.hasContent(tree)
  }

  override fun createViewer(): DiffEditorViewer {
    return createDefaultViewer("ChangesTreeDiffPreview")
  }

  protected fun createDefaultViewer(place: String) = createDefaultViewer(tree, handler, place)

  final override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer> {
    return handler.collectDiffProducers(tree, selectedOnly)
  }

  final override fun getEditorTabName(processor: DiffEditorViewer?): String? {
    val wrapper: Wrapper? = when (processor) {
      is ChangeViewDiffRequestProcessor -> processor.currentChange
      is CombinedDiffComponentProcessor -> (processor.currentBlock as? WrapperCombinedBlockProducer)?.wrapper
      else -> null
    }
    return getEditorTabName(wrapper)
  }

  @CalledInAny
  abstract fun getEditorTabName(wrapper: Wrapper?): String?

  private inner class PreviewOnNextDiffAction : DumbAwareAction() {
    init {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isOpenPreviewWithNextDiffShortcut()
    }

    override fun actionPerformed(e: AnActionEvent) {
      handleNextDiffShortcut()
    }
  }

  companion object {
    fun createDefaultViewer(changesTree: ChangesTree, previewHandler: ChangesTreeDiffPreviewHandler, place: String): DiffEditorViewer {
      val processor = if (CombinedDiffRegistry.isEnabled()) {
        CombinedDiffManager.getInstance(changesTree.project).createProcessor(place)
      }
      else {
        TreeHandlerDiffRequestProcessor(place, changesTree, previewHandler)
      }
      TreeHandlerChangesTreeTracker(changesTree, processor, previewHandler).track()
      return processor
    }
  }
}


abstract class ChangesTreeDiffPreviewHandler {
  open val isShowAllChangesForEmptySelection: Boolean get() = true

  abstract fun iterateSelectedChanges(tree: ChangesTree): Iterable<@JvmWildcard Wrapper>

  abstract fun iterateAllChanges(tree: ChangesTree): Iterable<@JvmWildcard Wrapper>

  abstract fun selectChange(tree: ChangesTree, change: Wrapper)

  fun hasContent(tree: ChangesTree): Boolean {
    val producers = if (isShowAllChangesForEmptySelection) iterateAllChanges(tree) else iterateSelectedChanges(tree)
    return JBIterable.from(producers).isNotEmpty
  }

  fun collectDiffProducers(tree: ChangesTree, selectedOnly: Boolean): ListSelection<out DiffRequestProducer> {
    val project = tree.project
    val producers = if (selectedOnly) iterateSelectedChanges(tree) else iterateAllChanges(tree)
    return ListSelection.create(producers.toList(), null)
      .withExplicitSelection(selectedOnly)
      .map { it.createProducer(project) }
  }
}

@ApiStatus.Internal
abstract class ChangesTreeDiffPreviewHandlerBase : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): JBIterable<Wrapper> {
    return collectWrappers(VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(tree: ChangesTree): JBIterable<Wrapper> {
    return collectWrappers(VcsTreeModelData.all(tree))
  }

  protected abstract fun collectWrappers(treeModelData: VcsTreeModelData): JBIterable<Wrapper>

  override fun selectChange(tree: ChangesTree, change: Wrapper) {
    val node = TreeUtil.findNodeWithObject(tree.root, change.userObject) ?: return
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
  }
}

object DefaultChangesTreeDiffPreviewHandler : ChangesTreeDiffPreviewHandlerBase() {
  override fun collectWrappers(treeModelData: VcsTreeModelData): JBIterable<Wrapper> {
    return treeModelData.iterateUserObjects(Change::class.java)
      .map { ChangeViewDiffRequestProcessor.ChangeWrapper(it) }
  }
}

private fun refreshCombinedDiffProcessor(tree: ChangesTree,
                                         processor: CombinedDiffComponentProcessor,
                                         handler: ChangesTreeDiffPreviewHandler,
                                         onlyBlockSelection: Boolean) {
  val combinedDiffViewer = processor.context.getUserData(COMBINED_DIFF_VIEWER_KEY)

  val selectedChanges = JBIterable.from(handler.iterateSelectedChanges(tree))

  val prevSelectedBlockId = combinedDiffViewer?.getCurrentBlockId() as? CombinedPathBlockId
  val keepSelection = prevSelectedBlockId != null && selectedChanges.find { it.toCombinedPathBlockId() == prevSelectedBlockId } != null
  val newSelectedBlockId = if (keepSelection) prevSelectedBlockId else selectedChanges.firstOrNull()?.toCombinedPathBlockId()

  if (onlyBlockSelection) {
    if (newSelectedBlockId != null && !keepSelection) {
      combinedDiffViewer?.scrollToFirstChange(newSelectedBlockId, false, CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
    }
  }
  else {
    processor.context.putUserData(COMBINED_DIFF_SCROLL_TO_BLOCK, newSelectedBlockId)

    val changes = handler.iterateAllChanges(tree).toList()
    if (changes.isNotEmpty()) {
      processor.setBlocks(prepareCombinedBlocksFromWrappers(tree.project, changes))
    }
  }
}

private fun Wrapper.toCombinedPathBlockId() = CombinedPathBlockId(filePath, fileStatus, tag)
