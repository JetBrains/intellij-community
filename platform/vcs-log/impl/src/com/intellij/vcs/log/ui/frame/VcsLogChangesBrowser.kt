// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.ide.ui.customization.CustomActionsSchema.Companion.getInstance
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager.Companion.getInstance
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.ValueTag
import com.intellij.openapi.vcs.history.VcsDiffUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.ScrollableContentBorder.Companion.setup
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.EventDispatcher
import com.intellij.util.Function
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
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.VcsLogActionIds
import com.intellij.vcs.log.util.VcsLogUiUtil
import com.intellij.vcs.log.util.VcsLogUtil
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.tree.DefaultTreeModel

/**
 * Change browser for commits in the Log. For merge commits, can display changes to the commits parents in separate groups.
 */
class VcsLogChangesBrowser internal constructor(project: Project,
                                                private val uiProperties: MainVcsLogUiProperties,
                                                private val dataGetter: Function<in CommitId, out VcsShortCommitDetails>,
                                                isWithEditorDiffPreview: Boolean,
                                                parent: Disposable) : AsyncChangesBrowserBase(project, false, false), Disposable {
  private var commitModel = CommitModel.createEmpty()
  private var isShowChangesFromParents = false
  private var isShowOnlyAffectedSelected = false

  private var affectedPaths: Collection<FilePath>? = null
  private val toolbarWrapper: Wrapper
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)
  private var editorDiffPreviewController: DiffPreviewController? = null

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

    val toolbarComponent = toolbar.component
    toolbarWrapper = Wrapper(toolbarComponent)
    GuiUtils.installVisibilityReferent(toolbarWrapper, toolbarComponent)

    init()

    if (isWithEditorDiffPreview) {
      setEditorDiffPreview()
      getInstance(myProject).subscribeToPreviewVisibilityChange(this) { setEditorDiffPreview() }
    }

    hideViewerBorder()
    setup(viewerScrollPane, Side.TOP)

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

  override fun dispose() {
    shutdown()
  }

  override fun createToolbarActions(): List<AnAction> {
    return ContainerUtil.append(
      super.createToolbarActions(),
      getInstance().getCorrectedAction(VcsLogActionIds.CHANGES_BROWSER_TOOLBAR_ACTION_GROUP)
    )
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return ContainerUtil.append(
      super.createPopupMenuActions(),
      ActionManager.getInstance().getAction(VcsLogActionIds.CHANGES_BROWSER_POPUP_ACTION_GROUP)
    )
  }

  private fun updateModel() {
    updateStatusText()
    viewer.requestRefresh { eventDispatcher.multicaster.onModelUpdated() }
  }

  fun resetSelectedDetails() {
    showText { text: StatusText -> text.setText("") }
  }

  fun showText(statusTextConsumer: Consumer<in StatusText>) {
    commitModel = CommitModel.createText(statusTextConsumer)
    updateModel()
  }

  fun setAffectedPaths(paths: Collection<FilePath>?) {
    affectedPaths = paths
    updateStatusText()
    myViewer.rebuildTree()
  }

  fun setSelectedDetails(detailsList: List<VcsFullCommitDetails>) {
    commitModel = createCommitModel(detailsList)
    updateModel()
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
      ) { uiProperties.set(MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS, true) }
    }
    else if (isShowOnlyAffectedSelected && affectedPaths != null) {
      emptyText.setText(VcsLogBundle.message("vcs.log.changes.no.changes.that.affect.selected.paths.status"))
        .appendSecondaryText(VcsLogBundle.message("vcs.log.changes.show.all.paths.status.action"),
                             SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
        ) { uiProperties.set(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES, false) }
    }
    else {
      emptyText.setText("")
    }
  }

  override val changesTreeModel: AsyncChangesTreeModel
    get() = MyVcsLogAsyncChangesTreeModel()

  private inner class MyVcsLogAsyncChangesTreeModel : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val model = commitModel
      val paths = affectedPaths
      val showOnlyAffectedSelected = isShowOnlyAffectedSelected
      val showChangesFromParents = isShowChangesFromParents

      val changes = collectAffectedChanges(model.changes, paths, showOnlyAffectedSelected)

      val changesToParents: MutableMap<CommitId, Collection<Change>> = LinkedHashMap()
      for ((key, value) in model.changesToParents) {
        changesToParents[key] = collectAffectedChanges(value, paths, showOnlyAffectedSelected)
      }

      val builder = TreeModelBuilder(myProject, grouping)
      builder.setChanges(changes, null)

      if (showChangesFromParents && !changesToParents.isEmpty()) {
        if (changes.isEmpty()) {
          builder.createTagNode(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.node"))
        }
        for (commitId in changesToParents.keys) {
          val changesFromParent = changesToParents[commitId]!!
          if (!changesFromParent.isEmpty()) {
            val parentNode: ChangesBrowserNode<*> = TagChangesBrowserNode(ParentTag(commitId.hash, getText(commitId)),
                                                                          SimpleTextAttributes.REGULAR_ATTRIBUTES, false)
            parentNode.markAsHelperNode()
            builder.insertSubtreeRoot(parentNode)
            builder.insertChanges(changesFromParent, parentNode)
          }
        }
      }
      return builder.build()
    }
  }

  private fun updateUiSettings() {
    isShowChangesFromParents = uiProperties.exists(MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS) &&
                               uiProperties.get(MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS)
    isShowOnlyAffectedSelected = uiProperties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
                                 uiProperties.get(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES)
  }

  val directChanges: List<Change>
    get() = commitModel.changes
  val selectedChanges: List<Change>
    get() = VcsTreeModelData.selected(myViewer).userObjects(Change::class.java)

  override fun getData(dataId: String): Any? {
    if (HAS_AFFECTED_FILES.`is`(dataId)) {
      return affectedPaths != null
    }
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.`is`(dataId)) {
      val roots: Set<VirtualFile> = HashSet(commitModel.roots)
      val selectedData = VcsTreeModelData.selected(myViewer)
      val superProvider = super.getData(dataId) as DataProvider?
      return CompositeDataProvider.compose(
        { slowId: String -> getSlowData(slowId, roots, selectedData) }, superProvider)
    }
    else if (QuickActionProvider.KEY.`is`(dataId)) {
      return object : QuickActionProvider {
        override fun getActions(originalProvider: Boolean): List<AnAction> {
          return SimpleToolWindowPanel.collectActions(this@VcsLogChangesBrowser)
        }

        override fun getComponent() = this@VcsLogChangesBrowser
        override fun getName() = null
      }
    }
    return super.getData(dataId)
  }

  private fun getSlowData(dataId: String,
                          roots: Set<VirtualFile>,
                          selectedData: VcsTreeModelData): Any? {
    if (VcsDataKeys.VCS.`is`(dataId)) {
      val rootsVcs = JBIterable.from(roots)
        .map { root: VirtualFile? -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root) }
        .filterNotNull()
        .unique()
        .single()
      if (rootsVcs != null) return rootsVcs.keyInstanceMethod

      val selectionVcs = selectedData.iterateUserObjects(Change::class.java)
        .map { change: Change? ->
          ChangesUtil.getFilePath(
            change!!)
        }
        .map { root: FilePath? -> ProjectLevelVcsManager.getInstance(myProject).getVcsFor(root) }
        .filterNotNull()
        .unique()
        .single()
      return selectionVcs?.keyInstanceMethod
    }
    return null
  }

  public override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    return getDiffRequestProducer(userObject, false)
  }

  fun getDiffRequestProducer(userObject: Any, forDiffPreview: Boolean): ChangeDiffRequestChain.Producer? {
    if (userObject !is Change) return null
    val context: MutableMap<Key<*>, Any> = HashMap()
    if (userObject !is MergedChange) {
      putRootTagIntoChangeContext(userObject, context)
    }
    return createDiffRequestProducer(myProject, userObject, context, forDiffPreview)
  }

  private fun setEditorDiffPreview() {
    val diffPreviewController = editorDiffPreviewController
    val isWithEditorDiffPreview = VcsLogUiUtil.isDiffPreviewInEditor(myProject)
    if (isWithEditorDiffPreview && diffPreviewController == null) {
      editorDiffPreviewController = VcsLogChangesBrowserDiffPreviewController()
    }
    else if (!isWithEditorDiffPreview && diffPreviewController != null) {
      diffPreviewController.activePreview.closePreview()
      editorDiffPreviewController = null
    }
  }

  fun createChangeProcessor(isInEditor: Boolean): VcsLogChangeProcessor {
    return VcsLogChangeProcessor(myProject, this, isInEditor, this)
  }

  override fun getShowDiffActionPreview(): DiffPreview? {
    val editorDiffPreviewController = editorDiffPreviewController
    return editorDiffPreviewController?.activePreview
  }

  fun selectChange(userObject: Any, tag: ChangesBrowserNode.Tag?) {
    viewer.invokeAfterRefresh { selectObjectWithTag(myViewer, userObject, tag) }
  }

  fun selectFile(toSelect: FilePath?) {
    viewer.invokeAfterRefresh { viewer.selectFile(toSelect) }
  }

  fun getTag(change: Change): ChangesBrowserNode.Tag? {
    val changesToParents = commitModel.changesToParents
    var parentId: CommitId? = null
    for (commitId in changesToParents.keys) {
      if (changesToParents[commitId]!!.contains(change)) {
        parentId = commitId
        break
      }
    }
    return if (parentId == null) null else ParentTag(parentId.hash, getText(parentId))
  }

  private fun putRootTagIntoChangeContext(change: Change, context: MutableMap<Key<*>, Any>) {
    val tag = getTag(change)
    if (tag != null) {
      context[ChangeDiffRequestProducer.TAG_KEY] = tag
    }
  }

  private fun getText(commitId: CommitId): @Nls String {
    var text = VcsLogBundle.message("vcs.log.changes.changes.to.parent.node", commitId.hash.toShortString())
    val detail = dataGetter.`fun`(commitId)
    if (detail !is LoadingDetails || detail is IndexedDetails) {
      text += " " + StringUtil.shortenTextWithEllipsis(detail.subject, 50, 0)
    }
    return text
  }

  fun interface Listener : EventListener {
    fun onModelUpdated()
  }

  private inner class VcsLogChangesBrowserDiffPreviewController : DiffPreviewControllerBase() {
    override val simplePreview: DiffPreview
      get() = VcsLogEditorDiffPreview(myProject, this@VcsLogChangesBrowser)

    override fun createCombinedDiffPreview(): CombinedDiffPreview {
      return VcsLogCombinedDiffPreview(this@VcsLogChangesBrowser)
    }
  }

  private class ParentTag(commit: Hash, private val text: @Nls String) : ValueTag<Hash>(commit) {
    override fun toString() = text
  }

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

    private fun createCommitModel(detailsList: List<VcsFullCommitDetails>): CommitModel {
      if (detailsList.isEmpty()) return CommitModel.createEmpty()
      val roots = ContainerUtil.map2Set(detailsList) { detail: VcsFullCommitDetails -> detail.root }
      val changes: MutableList<Change> = ArrayList()
      val changesToParents: MutableMap<CommitId, Set<Change>> = LinkedHashMap()
      if (detailsList.size == 1) {
        val detail = Objects.requireNonNull(ContainerUtil.getFirstItem(detailsList))
        changes.addAll(detail.changes)
        if (detail.parents.size > 1) {
          for (i in detail.parents.indices) {
            val changesSet: Set<Change> = ReferenceOpenHashSet(detail.getChanges(i))
            changesToParents[CommitId(detail.parents[i], detail.root)] = changesSet
          }
        }
      }
      else {
        changes.addAll(VcsLogUtil.collectChanges(detailsList) { obj: VcsFullCommitDetails -> obj.changes })
      }
      return CommitModel(roots, changes, changesToParents, null)
    }

    private fun collectAffectedChanges(changes: Collection<Change>,
                                       affectedPaths: Collection<FilePath>?,
                                       showOnlyAffectedSelected: Boolean): List<Change> {
      return if (!showOnlyAffectedSelected || affectedPaths == null) ArrayList(changes)
      else ContainerUtil.filter(changes) { change: Change? ->
        ContainerUtil.or(affectedPaths) { filePath: FilePath ->
          if (filePath.isDirectory) {
            return@or FileHistoryUtil.affectsDirectory(change!!, filePath)
          }
          else {
            return@or FileHistoryUtil.affectsFile(change!!, filePath, false) ||
                      FileHistoryUtil.affectsFile(change, filePath, true)
          }
        }
      }
    }

    fun createDiffRequestProducer(project: Project,
                                  change: Change,
                                  context: MutableMap<Key<*>, Any>,
                                  forDiffPreview: Boolean): ChangeDiffRequestChain.Producer? {
      if (change is MergedChange) {
        if (change.sourceChanges.size == 2) {
          if (forDiffPreview) {
            putFilePathsIntoMergedChangeContext(change, context)
          }
          return MergedChangeDiffRequestProvider.MyProducer(project, change, context)
        }
      }
      if (forDiffPreview) {
        VcsDiffUtil.putFilePathsIntoChangeContext(change, context)
      }
      return ChangeDiffRequestProducer.create(project, change, context)
    }

    private fun putFilePathsIntoMergedChangeContext(change: MergedChange, context: MutableMap<Key<*>, Any>) {
      val centerRevision = change.afterRevision
      val leftRevision = change.sourceChanges[0].beforeRevision
      val rightRevision = change.sourceChanges[1].beforeRevision
      val centerFile = centerRevision?.file
      val leftFile = leftRevision?.file
      val rightFile = rightRevision?.file
      context[DiffUserDataKeysEx.VCS_DIFF_CENTER_CONTENT_TITLE] = VcsDiffUtil.getRevisionTitle(centerRevision, centerFile, null)
      context[DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE] = VcsDiffUtil.getRevisionTitle(rightRevision, rightFile, centerFile)
      context[DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE] = VcsDiffUtil.getRevisionTitle(leftRevision, leftFile,
                                                                                             centerFile ?: rightFile)
    }
  }
}
