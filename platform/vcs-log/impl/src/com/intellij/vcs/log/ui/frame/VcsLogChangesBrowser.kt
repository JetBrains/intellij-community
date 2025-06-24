// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.combined.CombinedDiffManager
import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.tools.combined.DISABLE_LOADING_BLOCKS
import com.intellij.diff.util.CombinedDiffToggle
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.ui.customization.CustomActionsSchema.Companion.getInstance
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.AsyncChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.ui.ClientProperty
import com.intellij.ui.GuiUtils
import com.intellij.ui.ScrollableContentBorder.Companion.setup
import com.intellij.ui.Side
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.MergedChange
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.ui.frame.VcsLogAsyncChangesTreeModel.Companion.createDiffRequestProducer
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JScrollPane

/**
 * Change browser for commits in the Log. For merge commits, can display changes to the commits parents in separate groups.
 */
@ApiStatus.Internal
class VcsLogChangesBrowser(
  project: Project,
  private val model: VcsLogAsyncChangesTreeModel,
  parent: Disposable,
) : AsyncChangesBrowserBase(project, false, false), Disposable {
  private val toolbarWrapper: Wrapper

  init {
    Disposer.register(parent, this)

    toolbar.component.also { toolbarComponent ->
      toolbarWrapper = Wrapper(toolbarComponent)
      GuiUtils.installVisibilityReferent(toolbarWrapper, toolbarComponent)
    }

    init()

    hideViewerBorder()
    setup(viewerScrollPane, Side.TOP)

    getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.changes.accessible.name"))

    myViewer.setEmptyText(VcsLogBundle.message("vcs.log.changes.select.commits.to.view.changes.status"))
    myViewer.rebuildTree()

    model.addListener(this) {
      viewer.requestRefresh {
        VcsLogChangesTreeComponents.updateStatusText(model, viewer.emptyText)
      }
    }
  }

  override fun createToolbarComponent(): JComponent = toolbarWrapper

  override fun createCenterPanel(): JComponent {
    val centerPanel = super.createCenterPanel()
    val scrollPane = UIUtil.findComponentOfType(centerPanel, JScrollPane::class.java)
    if (scrollPane != null) {
      ClientProperty.put(scrollPane, UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP)
    }
    return centerPanel
  }

  fun setToolbarHeightReferent(referent: JComponent) {
    toolbarWrapper.setVerticalSizeReferent(referent)
  }

  override fun createToolbarActions(): List<AnAction> {
    return ContainerUtil.append(
      super.createToolbarActions(),
      getInstance().getCorrectedAction(VcsLogActionIds.CHANGES_BROWSER_TOOLBAR_ACTION_GROUP)
    )
  }

  override fun createLastToolbarActions(): List<AnAction> {
    return emptyList() // do not duplicate 'ChangesView.GroupBy' group
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      ActionManager.getInstance().getAction(VcsLogActionIds.CHANGES_BROWSER_POPUP_ACTION_GROUP)
    )
  }

  override val changesTreeModel: AsyncChangesTreeModel
    get() = model

  override fun dispose() {
    shutdown()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[QuickActionProvider.KEY] = ComponentQuickActionProvider(this@VcsLogChangesBrowser)
    VcsLogChangesTreeComponents.uiDataSnapshot(sink, model, viewer)
  }

  public override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    return getDiffRequestProducer(userObject, false)
  }

  fun getDiffRequestProducer(userObject: Any, forDiffPreview: Boolean): ChangeDiffRequestChain.Producer? {
    if (userObject !is Change) return null
    val context: MutableMap<Key<*>, Any> = HashMap()
    if (userObject !is MergedChange) {
      getTag(userObject)?.let { tag ->
        context[ChangeDiffRequestProducer.TAG_KEY] = tag
      }
    }
    return createDiffRequestProducer(myProject, userObject, context)
  }

  fun createChangeProcessor(isInEditor: Boolean): DiffEditorViewer {
    val place = if (isInEditor) DiffPlaces.DEFAULT else DiffPlaces.VCS_LOG_VIEW
    val handler = VcsLogDiffPreviewHandler(this)

    val processor: DiffEditorViewer
    if (CombinedDiffRegistry.isEnabled()) {
      processor = CombinedDiffManager.getInstance(myProject).createProcessor(place)
      processor.context.putUserData(DISABLE_LOADING_BLOCKS, true)
    }
    else {
      processor = VcsLogChangeProcessor(place, viewer, handler, true)
    }
    VcsLogTreeChangeProcessorTracker(viewer, processor, handler, !isInEditor).track()
    processor.context.putUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE, CombinedDiffToggle.DEFAULT)
    return processor
  }

  fun selectChange(userObject: Any, tag: ChangesBrowserNode.Tag?) {
    viewer.invokeAfterRefresh { selectObjectWithTag(myViewer, userObject, tag) }
  }

  fun selectFile(toSelect: FilePath?) {
    viewer.invokeAfterRefresh { viewer.selectFile(toSelect) }
  }

  fun getTag(change: Change): ChangesBrowserNode.Tag? = VcsLogChangesTreeComponents.getTag(model, change)
}
