// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.tools.combined.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor
import com.intellij.openapi.vcs.changes.DiffRequestProcessorWithProducers
import com.intellij.openapi.vcs.changes.EditorTabPreviewBase
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel.Companion.prepareCombinedDiffModelRequests
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.Delegates
import javax.swing.JComponent

@JvmField
internal val COMBINED_DIFF_PREVIEW_TAB_NAME = Key.create<() -> @NlsContexts.TabTitle String>("combined_diff_preview_tab_name")

abstract class CombinedDiffPreviewVirtualFile() : CombinedDiffVirtualFile("")

abstract class CombinedDiffPreview(project: Project,
                                   targetComponent: JComponent,
                                   needSetupOpenPreviewListeners: Boolean,
                                   parentDisposable: Disposable) :
  EditorTabPreviewBase(project, parentDisposable) {

  override val previewFile: VirtualFile by lazy {
    object : CombinedDiffPreviewVirtualFile() {
      override fun createModel(): CombinedDiffModel = getOrCreateModel()
    }
  }

  override val updatePreviewProcessor get() = getOrCreateModel()

  var model: CombinedDiffPreviewModel? = null
    private set

  private fun getOrCreateModel(): CombinedDiffPreviewModel {
    model?.let { return it }
    model = createModel().also {
      it.context.putUserData(COMBINED_DIFF_PREVIEW_TAB_NAME, ::getCombinedDiffTabTitle)
    }
    return model!!
  }

  override fun updatePreview(fromModelRefresh: Boolean) {
    super.updatePreview(fromModelRefresh)

    if (isPreviewOpen()) {
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(previewFile)
    }
  }

  init {
    escapeHandler = Runnable {
      closePreview()
      returnFocusToSourceComponent()
    }
    if (needSetupOpenPreviewListeners) {
      installNextDiffActionOn(targetComponent)
    }
  }

  protected open fun updatePreview() {
    val model = model ?: return
    if (model.ourDisposable.isDisposed) return
    model.context.putUserData(COMBINED_DIFF_VIEWER_KEY, null)
    val changes = model.iterateAllChanges().toList()
    if (changes.isNotEmpty()) {
      model.refresh(true)
      model.setBlocks(prepareCombinedDiffModelRequests(project, changes))
    }
  }

  open fun returnFocusToSourceComponent() = Unit

  override fun isPreviewOnDoubleClickAllowed(): Boolean = CombinedDiffRegistry.isEnabled() && super.isPreviewOnDoubleClickAllowed()
  override fun isPreviewOnEnterAllowed(): Boolean = CombinedDiffRegistry.isEnabled() && super.isPreviewOnEnterAllowed()

  protected abstract fun createModel(): CombinedDiffPreviewModel

  protected abstract fun getCombinedDiffTabTitle(): String

  override fun updateDiffAction(event: AnActionEvent) {
    event.presentation.isVisible = event.isFromActionToolbar || event.presentation.isEnabled
  }

  override fun getCurrentName(): String? = model?.selected?.presentableName
  override fun hasContent(): Boolean = !model?.requests.isNullOrEmpty()

  internal fun getFileSize(): Int = model?.requests?.size ?: 0
}

abstract class CombinedDiffPreviewModel(project: Project, parentDisposable: Disposable) :
  CombinedDiffModelImpl(project, parentDisposable), DiffPreviewUpdateProcessor, DiffRequestProcessorWithProducers {

  var selected by Delegates.equalVetoingObservable<Wrapper?>(null) { change ->
    if (change != null) {
      selectChangeInSourceComponent(change)
      scrollToChange(change)
    }
  }

  companion object {
    @JvmStatic
    fun prepareCombinedDiffModelRequests(project: Project, changes: List<Wrapper>): List<CombinedBlockProducer> {
      return changes
        .mapNotNull { wrapper ->
          val producer = wrapper.createProducer(project) ?: return@mapNotNull null
          val id = CombinedPathBlockId(wrapper.filePath, wrapper.fileStatus, wrapper.tag)
          CombinedBlockProducer(id, producer)
        }
    }

    @JvmStatic
    fun prepareCombinedDiffModelRequestsFromProducers(changes: List<ChangeDiffRequestChain.Producer>): List<CombinedBlockProducer> {
      return changes
        .map { wrapper ->
          val id = CombinedPathBlockId(wrapper.filePath, wrapper.fileStatus, null)
          CombinedBlockProducer(id, wrapper)
        }
    }

  }

  override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<DiffRequestProducer> {
    return ListSelection.create(requests.map { it.producer }, selected?.createProducer(project))
  }

  abstract fun iterateAllChanges(): Iterable<Wrapper>

  protected abstract fun iterateSelectedChanges(): Iterable<Wrapper>

  protected open fun showAllChangesForEmptySelection(): Boolean {
    return true
  }

  override fun clear() {
    cleanBlocks()
  }

  override fun refresh(fromModelRefresh: Boolean) {
    if (ourDisposable.isDisposed) return

    val selectedChanges = iterateSelectedOrAllChanges().toList()

    val selectedChange = selected?.let { prevSelected -> selectedChanges.find { it == prevSelected } }

    if (fromModelRefresh && selectedChange == null && selected != null &&
        context.isWindowFocused &&
        context.isFocusedInWindow) {
      // Do not automatically switch focused viewer
      if (selectedChanges.size == 1 && iterateAllChanges().any { it: Wrapper -> selected == it }) {
        selected?.run(::selectChangeInSourceComponent) // Restore selection if necessary
      }
      return
    }

    val newSelected = when {
      selectedChanges.isEmpty() -> null
      selectedChange == null -> selectedChanges[0]
      else -> selectedChange
    }

    newSelected?.let { context.putUserData(COMBINED_DIFF_SCROLL_TO_BLOCK, CombinedPathBlockId(it.filePath, it.fileStatus, it.tag)) }

    selected = newSelected
  }

  private fun iterateSelectedOrAllChanges(): Iterable<Wrapper> {
    return if (iterateSelectedChanges().none() && showAllChangesForEmptySelection()) iterateAllChanges() else iterateSelectedChanges()
  }

  private fun scrollToChange(change: Wrapper) {
    context.getUserData(COMBINED_DIFF_VIEWER_KEY)
      ?.scrollToFirstChange(CombinedPathBlockId(change.filePath, change.fileStatus, change.tag), false,
                            CombinedDiffViewer.ScrollPolicy.SCROLL_TO_BLOCK)
  }

  abstract fun selectChangeInSourceComponent(change: Wrapper)

  override fun getComponent(): JComponent = throw UnsupportedOperationException() //only for splitter preview
}
