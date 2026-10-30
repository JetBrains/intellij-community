// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.lang.changes

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesViewModifier
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.StructuralChangesViewSettings
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.openapi.vcs.changes.ui.StructuralHunkPresentation
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.ex.LineStatusTrackerI
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.vcs.impl.shared.rpc.StructuralHunkDto
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.ThreeStateCheckBox
import java.util.BitSet
import java.util.concurrent.atomic.AtomicBoolean

/** Adds a node for each change of a modified file. The structure of the file names the changes; the commit can include them one by one. */
internal class StructuralChangesViewModifier(private val project: Project) : ChangesViewModifier {
  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    if (builder !is TreeModelBuilder) return
    if (!StructuralChangesViewSettings.isEnabled(project)) return
    StructuralChangesViewUpdates.getInstance(project).start()

    val changeNodes = builder.myRoot.traverse().filter(ChangesBrowserChangeNode::class.java).toList()
    if (changeNodes.size > StructuralChangesViewSettings.maxFiles()) return

    val hunksService = StructuralHunksService.getInstance(project)
    for (node in changeNodes) {
      ProgressManager.checkCanceled()
      val change = node.getUserObject()
      val afterRevision = change.afterRevision as? CurrentContentRevision ?: continue
      // An added or a deleted file is one change; the file node shows it.
      val beforeRevision = change.beforeRevision ?: continue
      val path = afterRevision.file
      if (path.isDirectory || path.fileType.isBinary) continue
      val file = path.virtualFile ?: continue
      try {
        val changeListId = (change as? ChangeListChange)?.changeListId
        val fileHunks = hunksService.localChangeHunks(file, beforeRevision, changeListId) ?: continue
        for (hunk in fileHunks.hunks) {
          node.add(HunkNode(project, file, path, changeListId, hunk, fileHunks.partialCommit))
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        logger<StructuralChangesViewModifier>().warn("Cannot collect structural changes for ${path.path}", e)
      }
    }
  }
}

/** Identifies a hunk across tree rebuilds, so that the tree keeps the selection while the lines stay the same. */
private data class HunkKey(val path: FilePath, val changeListId: String?, val lines: HunkLines)

/** Reverts the selected hunks file by file. Any tracker can revert, so the hunks of a file without a partial tracker revert too. */
private object HunkRollback : ChangesBrowserStructuralNode.Rollback {
  override fun rollback(nodes: List<ChangesBrowserStructuralNode<*>>) {
    for (hunks in nodes.filterIsInstance<HunkNode>().groupBy { it.file }.values) {
      val first = hunks.first()
      first.withTracker(first::validTracker) { tracker -> first.rollback(tracker, hunks) }
    }
  }
}

/** A hunk of a modified file. It has a commit checkbox only when the commit can include the hunks of the file one by one. */
private class HunkNode(
  private val project: Project,
  val file: VirtualFile,
  path: FilePath,
  private val changeListId: String?,
  private val hunk: StructuralHunk,
  partialCommit: Boolean,
) : ChangesBrowserStructuralNode<HunkKey>(HunkKey(path, changeListId, hunk.lines), hasInclusion = partialCommit) {
  private val lines = hunk.lines
  private val presentation = StructuralHunkDto.of(lines, hunk.name)
  private val navigatable = OpenFileDescriptor(project, file, lines.line1, 0)

  override fun getNavigatable(): Navigatable = navigatable

  override fun getPreviewFile(): VirtualFile = file

  override fun getPreviewNavigationRequest(): NavigationRequest? =
    NavigationRequest.sourceNavigationRequest(project, file, navigatable.offset.takeIf { it >= 0 } ?: lineOffset())

  override fun getPreviewHighlightRange(): TextRange? {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
    if (document.modificationStamp != hunk.version.documentModificationStamp || lines.line1 == lines.line2) return null
    if (lines.line2 > document.lineCount) return null
    return TextRange(document.getLineStartOffset(lines.line1), document.getLineEndOffset(lines.line2 - 1))
  }

  override fun getDiffLines(): DiffLines = presentation.diffLines

  override fun getInclusionState(fileState: ThreeStateCheckBox.State): ThreeStateCheckBox.State {
    val tracker = validPartialTracker() ?: return fileState
    if (tracker.document.modificationStamp != hunk.version.documentModificationStamp) return fileState
    val range = findRanges(tracker).singleOrNull() as? LocalRange ?: return fileState
    val exclusion = range.exclusionState
    return when {
      !exclusion.hasExcluded -> ThreeStateCheckBox.State.SELECTED
      !exclusion.hasIncluded -> ThreeStateCheckBox.State.NOT_SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  @RequiresEdt
  override fun setIncluded(included: Boolean) {
    withTracker(::validPartialTracker) { setExcluded(it, !included) }
  }

  override fun getRollback(): Rollback = HunkRollback

  /**
   * Runs the action with the tracker that [find] gives. A file has a tracker only while an editor shows it, so a file without one gets a
   * tracker until the action ends. A partial state keeps the new tracker after the release.
   *
   * The manager reports the new tracker in any modality. The action can change the document, and only a write-safe context allows that,
   * so the action runs later in the modality of this call.
   */
  @RequiresEdt
  fun <T : LineStatusTrackerI<*>> withTracker(find: () -> T?, action: (T) -> Unit) {
    find()?.let {
      action(it)
      return
    }
    val manager = LineStatusTrackerManager.getInstanceImpl(project)
    val requester = Any()
    val document = ReadAction.computeBlocking<Document?, RuntimeException> { FileDocumentManager.getInstance().getDocument(file) } ?: return
    if (document.modificationStamp != hunk.version.documentModificationStamp) {
      refreshTree()
      return
    }
    val modality = ModalityState.defaultModalityState()
    ReadAction.computeBlocking<Unit, RuntimeException> { manager.requestTrackerFor(document, requester) }
    manager.invokeAfterUpdate {
      ApplicationManager.getApplication().invokeLater({
        try {
          find()?.let(action)
        }
        finally {
          ReadAction.computeBlocking<Unit, RuntimeException> { manager.releaseTrackerFor(document, requester) }
        }
      }, modality, project.disposed)
    }
  }

  /**
   * Reverts the ranges of the hunks of this file in one global command, so that Undo in the tree brings them back with no question. A
   * stale hunk reverts nothing and refreshes the tree.
   */
  @RequiresEdt
  fun rollback(tracker: LineStatusTrackerI<*>, hunks: List<HunkNode>) {
    if (hunks.any { !it.describes(tracker) }) {
      refreshTree()
      return
    }
    val lines = BitSet()
    for (range in hunks.flatMap { it.findRanges(tracker) }) {
      if (range.line1 == range.line2) lines.set(range.line1) else lines.set(range.line1, range.line2)
    }
    if (lines.isEmpty) return
    val commands = CommandProcessor.getInstance()
    commands.executeCommand(project, {
      commands.markCurrentCommandAsGlobal(project)
      tracker.rollbackChanges(lines)
    }, DiffBundle.message("rollback.change.command.name"), null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION)
  }

  /** Changes the ranges of the hunk. A stale hunk changes nothing and refreshes the tree, so that the new nodes show the current hunks. */
  private fun setExcluded(tracker: PartialLocalLineStatusTracker, excluded: Boolean) {
    if (!describes(tracker)) {
      refreshTree()
      return
    }
    for (range in findRanges(tracker)) {
      tracker.setExcludedFromCommit(range, excluded)
    }
  }

  /** True while the tracker compares the document version of the hunk with the base of the hunk. */
  private fun describes(tracker: LineStatusTrackerI<*>): Boolean {
    val version = hunk.version
    if (tracker.document.modificationStamp != version.documentModificationStamp) return false
    val trackerBaseHash = version.trackerBaseHash
    if (trackerBaseHash != null) return StringUtil.stringHashCode(tracker.vcsDocument.immutableCharSequence) == trackerBaseHash
    return ChangeListManager.getInstance(project).getChange(file)?.beforeRevision?.revisionNumber?.asString() == version.baseRevision
  }

  private fun refreshTree() {
    project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
  }

  /** A tracker of the file that follows the document. Any tracker can revert a hunk. */
  fun validTracker(): LineStatusTrackerI<*>? =
    LineStatusTrackerManager.getInstance(project).getLineStatusTracker(file)?.takeIf { it.isValid() }

  /** A partial tracker of the file that follows the document. Only a partial tracker can keep a hunk out of the commit. */
  private fun validPartialTracker(): PartialLocalLineStatusTracker? =
    PartialChangesUtil.getPartialTracker(project, file)?.takeIf { it.isValid() }

  /**
   * The tracker range with the same lines, or the ranges that overlap the lines when the tracker diff differs from the tree. Only the
   * ranges of a partial tracker have a changelist. A file without a partial tracker has all its ranges in one changelist.
   */
  private fun findRanges(tracker: LineStatusTrackerI<*>): List<Range> {
    val ranges = tracker.getRanges()?.filter { changeListId == null || it !is LocalRange || it.changelistId == changeListId }
                 ?: return emptyList()
    ranges.firstOrNull { it.line1 == lines.line1 && it.line2 == lines.line2 && it.vcsLine1 == lines.vcsLine1 && it.vcsLine2 == lines.vcsLine2 }
      ?.let { return listOf(it) }
    return ranges.filter {
      it.line1 < maxOf(lines.line2, lines.line1 + 1) && lines.line1 < maxOf(it.line2, it.line1 + 1)
    }
  }

  private fun lineOffset(): Int {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return 0
    return if (lines.line1 < document.lineCount) document.getLineStartOffset(lines.line1) else document.textLength
  }

  override fun getTextPresentation(): String = StructuralHunkPresentation.text(presentation)

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    StructuralHunkPresentation.render(renderer, presentation)
  }

  override fun getSortWeight(): Int = HUNK_SORT_WEIGHT

  override fun compareUserObjects(o2: HunkKey): Int = compareValuesBy(getUserObject(), o2, { it.lines.line1 }, { it.lines.vcsLine1 })

  private companion object {
    const val HUNK_SORT_WEIGHT = 9
  }
}

/**
 * Refreshes the Changes tree when the hunks can change: on a change of the PSI of a modified file, at the end of indexing, and on a change
 * of the registry key. The first tree build with structural changes starts it, so the listeners exist only after the structural tree was on.
 */
@Service(Service.Level.PROJECT)
internal class StructuralChangesViewUpdates(private val project: Project) : Disposable {
  private val started = AtomicBoolean()

  /** Starts the listeners once. Before the first start, a change of the registry key shows the hunks with the next refresh of the tree. */
  fun start() {
    if (!started.compareAndSet(false, true)) return
    PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeAnyChangeAbstractAdapter() {
      override fun onChange(file: PsiFile?) {
        val virtualFile = file?.virtualFile ?: return
        if (ChangeListManager.getInstance(project).getChange(virtualFile) != null) refresh()
      }
    }, this)
    project.messageBus.connect(this).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      // A tree build in dumb mode shows no hunks.
      override fun exitDumbMode() = refresh()
    })
    RegistryManager.getInstance().get(StructuralChangesViewSettings.REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        if (!project.isDisposed) project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
      }
    }, this)
  }

  private fun refresh() {
    if (StructuralChangesViewSettings.isEnabled(project)) {
      project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
    }
  }

  override fun dispose() {}

  companion object {
    fun getInstance(project: Project): StructuralChangesViewUpdates = project.service()
  }
}
