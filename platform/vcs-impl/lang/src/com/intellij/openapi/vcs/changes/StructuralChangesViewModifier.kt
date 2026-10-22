// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.LineRange
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ThreeStateCheckBox
import java.util.concurrent.Callable

/** Adds a node for each change of a modified file. The structure of the file names the changes; the commit can include them one by one. */
internal class StructuralChangesViewModifier(private val project: Project) : ChangesViewModifier {
  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    if (builder !is TreeModelBuilder) return
    project.service<StructuralChangesViewUpdates>()
    if (!StructuralChangesViewSettings.isEnabled(project)) return

    val changeNodes = builder.myRoot.traverse().filter(ChangesBrowserChangeNode::class.java).toList()
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
        val baseContent = beforeRevision.content ?: continue
        if (baseContent.length > MAX_CONTENT_LENGTH) continue
        val changeListId = (change as? ChangeListChange)?.changeListId
        val baseRevision = beforeRevision.revisionNumber
        val hunks = ReadAction.nonBlocking(Callable { collectHunks(file, baseContent, baseRevision, changeListId) })
          .expireWith(project).executeSynchronously()
        for (hunk in hunks) {
          node.add(HunkNode(project, file, path, changeListId, hunk))
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        logger<StructuralChangesViewModifier>().warn("Cannot collect structural changes for ${path.path}", e)
      }
    }
  }

  private fun collectHunks(file: VirtualFile, baseContent: String, baseRevision: VcsRevisionNumber, changeListId: String?): List<Hunk> {
    if (DumbService.isDumb(project) || !file.isValid) return emptyList()
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
    val documentModificationStamp = document.modificationStamp
    // The ranges of a valid tracker are the ones that the commit uses.
    val diff = PartialChangesUtil.getPartialTracker(project, file)?.takeIf { it.isValid() }?.let { readTracker(it, changeListId) }
               ?: compareLines(file, document, baseContent)
    if (diff.currentText.length > MAX_CONTENT_LENGTH) return emptyList()
    val original = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
    val before = StructuralHunkNamer.createSnapshot(original, diff.baseText) ?: return emptyList()
    val after = StructuralHunkNamer.createSnapshot(original, diff.currentText) ?: return emptyList()
    val names = StructuralHunkNamer.name(before, after, diff.hunks)
    val version = FileVersion(documentModificationStamp, baseRevision.asString(), diff.trackerBaseHash)
    return diff.hunks.zip(names) { lines, name -> Hunk(lines, name, version) }
  }

  private fun readTracker(tracker: PartialLocalLineStatusTracker, changeListId: String?): FileDiff? = tracker.readLock {
    val ranges = tracker.getRanges() ?: return@readLock null
    val baseText = tracker.vcsDocument.immutableCharSequence.toString()
    FileDiff(baseText, tracker.document.immutableCharSequence.toString(),
             ranges.filter { changeListId == null || it.changelistId == changeListId }.map { HunkLines(it.vcsLine1, it.vcsLine2, it.line1, it.line2) },
             StringUtil.stringHashCode(baseText))
  }

  private fun compareLines(file: VirtualFile, document: Document, baseContent: String): FileDiff {
    val baseText = StringUtil.convertLineSeparators(TextPresentationTransformers.fromPersistent(baseContent, file).toString())
    val currentText = document.immutableCharSequence.toString()
    val indicator = ProgressManager.getInstance().progressIndicator ?: DumbProgressIndicator.INSTANCE
    val fragments = ComparisonManager.getInstance().compareLines(baseText, currentText, ComparisonPolicy.DEFAULT, indicator)
    return FileDiff(baseText, currentText, fragments.map { HunkLines(it.startLine1, it.endLine1, it.startLine2, it.endLine2) }, null)
  }

  /** The hunks of a file. A diff of a tracker has the hash of the tracker base text. */
  private class FileDiff(val baseText: String, val currentText: String, val hunks: List<HunkLines>, val trackerBaseHash: Int?)

  private companion object {
    const val MAX_CONTENT_LENGTH = 1_000_000
  }
}

private class Hunk(val lines: HunkLines, val name: HunkName, val version: FileVersion)

/**
 * The document version and the base that the hunks of a file describe. The tracker base text identifies the base of the hunks of a tracker.
 * Other hunks use the base revision, because a new tracker loads the base of the current change.
 */
private class FileVersion(val documentModificationStamp: Long, val baseRevision: String, val trackerBaseHash: Int?)

/** Identifies a hunk across tree rebuilds, so that the tree keeps the selection while the lines stay the same. */
private data class HunkKey(val path: FilePath, val changeListId: String?, val lines: HunkLines)

private class HunkNode(
  private val project: Project,
  private val file: VirtualFile,
  path: FilePath,
  private val changeListId: String?,
  private val hunk: Hunk,
) : ChangesBrowserStructuralNode<HunkKey>(HunkKey(path, changeListId, hunk.lines), hasInclusion = true) {
  private val lines = hunk.lines
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

  override fun getDiffLines(): DiffLines = DiffLines(LineRange(lines.vcsLine1, lines.vcsLine2), LineRange(lines.line1, lines.line2))

  override fun getInclusionState(fileState: ThreeStateCheckBox.State): ThreeStateCheckBox.State {
    val tracker = validTracker() ?: return fileState
    if (tracker.document.modificationStamp != hunk.version.documentModificationStamp) return fileState
    val range = findRanges(tracker).singleOrNull() ?: return fileState
    val exclusion = range.exclusionState
    return when {
      !exclusion.hasExcluded -> ThreeStateCheckBox.State.SELECTED
      !exclusion.hasIncluded -> ThreeStateCheckBox.State.NOT_SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  @RequiresEdt
  override fun setIncluded(included: Boolean) {
    val tracker = validTracker()
    if (tracker != null) {
      setExcluded(tracker, !included)
      return
    }
    // A file has a tracker only while an editor shows it. The partial state keeps the new tracker after the release.
    val manager = LineStatusTrackerManager.getInstanceImpl(project)
    val requester = Any()
    val document = ReadAction.computeBlocking<Document?, RuntimeException> { FileDocumentManager.getInstance().getDocument(file) } ?: return
    if (document.modificationStamp != hunk.version.documentModificationStamp) {
      refreshTree()
      return
    }
    ReadAction.computeBlocking<Unit, RuntimeException> { manager.requestTrackerFor(document, requester) }
    manager.invokeAfterUpdate {
      try {
        validTracker()?.let { setExcluded(it, !included) }
      }
      finally {
        ReadAction.computeBlocking<Unit, RuntimeException> { manager.releaseTrackerFor(document, requester) }
      }
    }
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
  private fun describes(tracker: PartialLocalLineStatusTracker): Boolean {
    val version = hunk.version
    if (tracker.document.modificationStamp != version.documentModificationStamp) return false
    val trackerBaseHash = version.trackerBaseHash
    if (trackerBaseHash != null) return StringUtil.stringHashCode(tracker.vcsDocument.immutableCharSequence) == trackerBaseHash
    return ChangeListManager.getInstance(project).getChange(file)?.beforeRevision?.revisionNumber?.asString() == version.baseRevision
  }

  private fun refreshTree() {
    project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
  }

  private fun validTracker(): PartialLocalLineStatusTracker? = PartialChangesUtil.getPartialTracker(project, file)?.takeIf { it.isValid() }

  /** The tracker range with the same lines, or the ranges that overlap the lines when the tracker diff differs from the tree. */
  private fun findRanges(tracker: PartialLocalLineStatusTracker): List<LocalRange> {
    val ranges = tracker.getRanges()?.filter { changeListId == null || it.changelistId == changeListId } ?: return emptyList()
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

  override fun getTextPresentation(): String {
    if (hunk.name.parts.isEmpty()) return VcsBundle.message("changes.structural.blank.lines")
    return joinedParts()
  }

  /** The parts are source text, and the separators are symbols, so the text needs no translation. */
  private fun joinedParts(): @NlsSafe String =
    hunk.name.parts.joinToString(", ") { part -> part.previousText?.let { "$it → ${part.text}" } ?: part.text }

  private fun tooltip(): @NlsSafe String = listOfNotNull(getTextPresentation(), hunk.name.detail?.text).joinToString("  ")

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val name = hunk.name
    renderer.icon = name.icon ?: EmptyIcon.ICON_16
    if (name.parts.isEmpty()) {
      renderer.append(VcsBundle.message("changes.structural.blank.lines"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      return
    }
    name.parts.forEachIndexed { index, part ->
      if (index > 0) renderer.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      part.previousText?.let {
        renderer.append(it, attributes(FileStatus.DELETED))
        renderer.append(" → ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      renderer.append(part.text, attributes(part.status))
    }
    if (name.isWhitespaceOnly) {
      renderer.append("  " + VcsBundle.message("changes.structural.whitespace.only"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    name.detail?.let {
      val style = if (it.status == FileStatus.DELETED) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN
      renderer.append("  " + it.text, SimpleTextAttributes(style, SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor))
    }
    renderer.toolTipText = tooltip()
  }

  private fun attributes(status: FileStatus): SimpleTextAttributes {
    val style = if (status == FileStatus.DELETED) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN
    return SimpleTextAttributes(style, status.color)
  }

  override fun getSortWeight(): Int = HUNK_SORT_WEIGHT

  override fun compareUserObjects(o2: HunkKey): Int = compareValuesBy(getUserObject(), o2, { it.lines.line1 }, { it.lines.vcsLine1 })

  private companion object {
    const val HUNK_SORT_WEIGHT = 9
  }
}

@Service(Service.Level.PROJECT)
internal class StructuralChangesViewUpdates(private val project: Project) : Disposable {
  init {
    project.messageBus.connect(this).subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { refresh() })
    RegistryManager.getInstance().get(StructuralChangesViewSettings.REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
      }
    }, this)
  }

  private fun refresh() {
    if (StructuralChangesViewSettings.isEnabled(project)) {
      project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
    }
  }

  override fun dispose() {}
}
