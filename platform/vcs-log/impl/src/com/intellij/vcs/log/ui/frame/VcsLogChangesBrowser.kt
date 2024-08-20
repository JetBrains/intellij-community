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
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.ValueTag
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.ScrollableContentBorder.Companion.setup
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.history.FileHistoryUtil
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.MergedChange
import com.intellij.vcs.log.impl.MergedChangeDiffRequestProvider
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.util.VcsLogUtil
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.tree.DefaultTreeModel

/**
 * Change browser for commits in the Log. For merge commits, can display changes to the commits parents in separate groups.
 */
class VcsLogChangesBrowser internal constructor(project: Project,
                                                private val uiProperties: VcsLogUiProperties,
                                                private val dataGetter: (CommitId) -> VcsShortCommitDetails,
                                                isWithEditorDiffPreview: Boolean,
                                                parent: Disposable) : AsyncChangesBrowserBase(project, false, false), Disposable {
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)
  private val toolbarWrapper: Wrapper

  private val unprocessedSelection = AtomicReference<Selection?>(null)

  private var commitModel = CommitModel.createEmpty()
  private var isShowChangesFromParents = false
  private var isShowOnlyAffectedSelected = false

  private var affectedPaths: Collection<FilePath>? = null

  init {
    val propertiesChangeListener = object : PropertiesChangeListener {
      override fun <T> onPropertyChanged(property: VcsLogUiProperty<T>) {
        updateUiSettings()
        if (MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS == property || MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES == property) {
          myViewer.rebuildTree()
          updateStatusText()
        }
      }
    }
    uiProperties.addChangeListener(propertiesChangeListener, this)
    updateUiSettings()

    Disposer.register(parent, this)

    toolbar.component.also { toolbarComponent ->
      toolbarWrapper = Wrapper(toolbarComponent)
      GuiUtils.installVisibilityReferent(toolbarWrapper, toolbarComponent)
    }

    init()

    showDiffActionPreview = if (isWithEditorDiffPreview) VcsLogEditorDiffPreview(this) else null

    hideViewerBorder()
    setup(viewerScrollPane, Side.TOP)

    getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.changes.accessible.name"))

    myViewer.setEmptyText(VcsLogBundle.message("vcs.log.changes.select.commits.to.view.changes.status"))
    myViewer.rebuildTree()
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

  fun addListener(listener: Listener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
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

  fun setSelectedDetails(detailsList: List<VcsFullCommitDetails>) {
    unprocessedSelection.set(Selection(detailsList, null))
    updateModel()
  }

  fun setEmpty() {
    setEmptyWithText { it.setText("") }
  }

  fun setEmptyWithText(statusTextConsumer: Consumer<in StatusText>) {
    unprocessedSelection.set(Selection(emptyList(), statusTextConsumer))
    updateModel()
  }

  fun setAffectedPaths(paths: Collection<FilePath>?) {
    affectedPaths = paths
    updateStatusText()
    myViewer.rebuildTree()
  }

  private fun updateModel() {
    viewer.requestRefresh {
      updateStatusText()
      eventDispatcher.multicaster.onModelUpdated()
    }
  }

  private fun updateStatusText() {
    val emptyText = myViewer.emptyText
    val model = commitModel

    val customStatus = model.customEmptyTextStatus
    if (customStatus != null) {
      customStatus.accept(emptyText)
      return
    }
    if (model.roots.isEmpty()) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.select.commits.to.view.changes.status"))
    }
    else if (!model.changesToParents.isEmpty()) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.status")).appendSecondaryText(
        VcsLogBundle.message("vcs.log.changes.show.changes.to.parents.status.action"),
        SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
      ) { uiProperties[MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS] = true }
    }
    else if (isShowOnlyAffectedSelected && affectedPaths != null) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.changes.that.affect.selected.paths.status"))
        .appendSecondaryText(VcsLogBundle.message("vcs.log.changes.show.all.paths.status.action"),
                             SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) { uiProperties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES] = false }
    }
    else {
      emptyText.setText("")
    }
  }

  override val changesTreeModel: AsyncChangesTreeModel
    get() = MyVcsLogAsyncChangesTreeModel()

  override fun dispose() {
    shutdown()
    unprocessedSelection.set(null)
  }

  private inner class MyVcsLogAsyncChangesTreeModel : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val selection = unprocessedSelection.getAndSet(null)
      if (selection != null) {
        try {
          commitModel = selection.createCommitModel()
        }
        catch (e: ProcessCanceledException) {
          unprocessedSelection.compareAndSet(null, selection)
          throw e
        }
      }
      return buildTreeModelSync(commitModel, affectedPaths, isShowOnlyAffectedSelected, isShowChangesFromParents, grouping)
    }

    private fun buildTreeModelSync(commitModel: CommitModel,
                                   affectedPaths: Collection<FilePath>?,
                                   showOnlyAffectedSelected: Boolean,
                                   showChangesFromParents: Boolean,
                                   grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val changes = collectAffectedChanges(commitModel.changes, affectedPaths, showOnlyAffectedSelected)
      val changesToParents = commitModel.changesToParents.mapValues {
        collectAffectedChanges(it.value, affectedPaths, showOnlyAffectedSelected)
      }

      val builder = TreeModelBuilder(myProject, grouping)
      builder.setChanges(changes, null)

      if (showChangesFromParents && !changesToParents.isEmpty()) {
        if (changes.isEmpty()) {
          builder.createTagNode(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.node"))
        }
        for ((commitId, changesFromParent) in changesToParents) {
          if (changesFromParent.isEmpty()) continue

          val parentNode: ChangesBrowserNode<*> = TagChangesBrowserNode(ParentTag(commitId.hash, getText(commitId)),
                                                                        SimpleTextAttributes.REGULAR_ATTRIBUTES, false)
          parentNode.markAsHelperNode()
          builder.insertSubtreeRoot(parentNode)
          builder.insertChanges(changesFromParent, parentNode)
        }
      }
      return builder.build()
    }
  }

  private fun updateUiSettings() {
    isShowChangesFromParents = uiProperties.exists(MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS) &&
                               uiProperties[MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS]
    isShowOnlyAffectedSelected = uiProperties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
                                 uiProperties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES]
  }

  val directChanges: List<Change>
    get() = commitModel.changes
  val selectedChanges: List<Change>
    get() = VcsTreeModelData.selected(myViewer).userObjects(Change::class.java)

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[HAS_AFFECTED_FILES] = affectedPaths != null
    sink[QuickActionProvider.KEY] = ComponentQuickActionProvider(this@VcsLogChangesBrowser)

    val roots = HashSet(commitModel.roots)
    val selectedData = VcsTreeModelData.selected(myViewer)
    sink.lazy(VcsDataKeys.VCS) {
      getSelectedVcs(roots, selectedData)?.keyInstanceMethod
    }
  }

  private fun getSelectedVcs(
    roots: Set<VirtualFile>,
    selectedData: VcsTreeModelData,
  ): AbstractVcs? {
    val rootsVcs = JBIterable.from(roots)
      .filterMap { root -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root) }
      .unique()
      .single()
    if (rootsVcs != null) return rootsVcs

    val selectionVcs = selectedData.iterateUserObjects(Change::class.java)
      .map { change -> ChangesUtil.getFilePath(change) }
      .filterMap { root -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root) }
      .unique()
      .single()
    return selectionVcs
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
      processor = VcsLogChangeProcessor(place, this, handler, true)
    }
    VcsLogTreeChangeProcessorTracker(this, processor, handler, !isInEditor).track()
    processor.context.putUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE, CombinedDiffToggle.DEFAULT)
    return processor
  }

  fun selectChange(userObject: Any, tag: ChangesBrowserNode.Tag?) {
    viewer.invokeAfterRefresh { selectObjectWithTag(myViewer, userObject, tag) }
  }

  fun selectFile(toSelect: FilePath?) {
    viewer.invokeAfterRefresh { viewer.selectFile(toSelect) }
  }

  fun getTag(change: Change): ChangesBrowserNode.Tag? {
    val changesToParents = commitModel.changesToParents
    val parentId = changesToParents.entries.firstOrNull { it.value.contains(change) }?.key ?: return null
    return ParentTag(parentId.hash, getText(parentId))
  }

  private fun getText(commitId: CommitId): @Nls String {
    var text = VcsLogBundle.message("vcs.log.changes.changes.to.parent.node", commitId.hash.toShortString())
    val detail = dataGetter(commitId)
    if (detail !is LoadingDetails || detail is IndexedDetails) {
      text += " " + StringUtil.shortenTextWithEllipsis(detail.subject, 50, 0)
    }
    return text
  }

  fun interface Listener : EventListener {
    fun onModelUpdated()
  }

  private class ParentTag(commit: Hash, private val text: @Nls String) : ValueTag<Hash>(commit) {
    override fun toString() = text
  }

  private data class Selection(val details: List<VcsFullCommitDetails>, val emptyText: Consumer<in StatusText>? = null)

  private class CommitModel(val roots: Set<VirtualFile>,
                            val changes: List<Change>,
                            val changesToParents: Map<CommitId, Set<Change>>,
                            val customEmptyTextStatus: Consumer<in StatusText>?) {
    companion object {
      fun createEmpty(): CommitModel {
        return CommitModel(emptySet(), emptyList(), emptyMap(), null)
      }

      fun createText(statusTextConsumer: Consumer<in StatusText>?): CommitModel {
        return CommitModel(emptySet(), emptyList(), emptyMap(), statusTextConsumer)
      }
    }
  }

  companion object {
    @JvmField
    val HAS_AFFECTED_FILES = DataKey.create<Boolean>("VcsLogChangesBrowser.HasAffectedFiles")

    private fun Selection.createCommitModel(): CommitModel {
      if (details.isEmpty()) return CommitModel.createText(emptyText)

      val roots = details.map(VcsFullCommitDetails::getRoot).toSet()

      val singleCommitDetail = details.singleOrNull()
      if (singleCommitDetail == null) {
        return CommitModel(roots, VcsLogUtil.collectChanges(details), emptyMap(), null)
      }

      val changesToParents = if (singleCommitDetail.parents.size > 1) {
        singleCommitDetail.parents.indices.associate { i ->
          CommitId(singleCommitDetail.parents[i], singleCommitDetail.root) to ReferenceOpenHashSet(singleCommitDetail.getChanges(i))
        }
      }
      else emptyMap()

      return CommitModel(roots, singleCommitDetail.changes.toList(), changesToParents, null)
    }

    private fun collectAffectedChanges(changes: Collection<Change>,
                                       affectedPaths: Collection<FilePath>?,
                                       showOnlyAffectedSelected: Boolean): List<Change> {
      return if (!showOnlyAffectedSelected || affectedPaths == null) ArrayList(changes)
      else changes.filter { change: Change ->
        affectedPaths.any { filePath: FilePath ->
          if (filePath.isDirectory) {
            return@any FileHistoryUtil.affectsDirectory(change, filePath)
          }
          else {
            return@any FileHistoryUtil.affectsFile(change, filePath, false) ||
                       FileHistoryUtil.affectsFile(change, filePath, true)
          }
        }
      }
    }

    fun createDiffRequestProducer(
      project: Project,
      change: Change,
      context: MutableMap<Key<*>, Any>,
    ): ChangeDiffRequestChain.Producer? =
      if (change is MergedChange && change.sourceChanges.size == 2)
        MergedChangeDiffRequestProvider.MyProducer(project, change, context)
      else
        ChangeDiffRequestProducer.create(project, change, context)
  }
}
