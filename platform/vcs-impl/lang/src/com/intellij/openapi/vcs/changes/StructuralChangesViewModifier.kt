// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.LineRange
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
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
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
import com.intellij.openapi.vcs.ex.LineStatusTrackerI
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.Nls
import java.util.BitSet
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Adds a node for each change of a modified file. The structure of the file names the changes; the commit can include them one by one. */
internal class StructuralChangesViewModifier(private val project: Project) : ChangesViewModifier {
  /** The hunks of the last tree build. A rebuild names again only the files whose inputs changed. */
  private val hunkCache = ConcurrentHashMap<HunkSource, List<Hunk>>()

  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    if (builder !is TreeModelBuilder) return
    if (!StructuralChangesViewSettings.isEnabled(project)) {
      hunkCache.clear()
      return
    }
    StructuralChangesViewUpdates.getInstance(project).start()

    val changeNodes = builder.myRoot.traverse().filter(ChangesBrowserChangeNode::class.java).toList()
    // The first build names the hunks of each file, so a large change would keep the tree waiting.
    if (changeNodes.size > Registry.intValue(MAX_FILES_KEY)) {
      hunkCache.clear()
      return
    }

    val sources = HashSet<HunkSource>()
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
        val fileHunks = collectHunks(file, beforeRevision, changeListId) ?: continue
        sources += fileHunks.source
        for (hunk in fileHunks.hunks) {
          node.add(HunkNode(project, file, path, changeListId, hunk, fileHunks.partialCommit))
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        logger<StructuralChangesViewModifier>().warn("Cannot collect structural changes for ${path.path}", e)
      }
    }
    hunkCache.keys.retainAll(sources)
  }

  /**
   * The hunks of a file, and whether the commit can include them one by one. The cache or a tracker gives the hunks without the base content.
   * A load of the base content can take long, so it runs outside a read action and only when the hunks need it.
   */
  private fun collectHunks(file: VirtualFile, beforeRevision: ContentRevision, changeListId: String?): FileHunks? {
    val baseRevision = beforeRevision.revisionNumber
    // A revision number identifies the base content, so only a base without a number needs its content to find the cached hunks.
    if (baseRevision != VcsRevisionNumber.NULL) {
      val read = readNonBlocking { readHunks(file, baseRevision, changeListId, baseContent = null) }
      if (read != NeedsBaseContent) return read as? FileHunks
    }
    val baseContent = beforeRevision.content ?: return null
    if (baseContent.length > MAX_CONTENT_LENGTH) return null
    return readNonBlocking { readHunks(file, baseRevision, changeListId, baseContent) } as? FileHunks
  }

  /**
   * Reads the hunks of a file. The commit can include them one by one only with a partial tracker, and only some VCSs, settings, and file
   * statuses give one. Without [baseContent], a file that has no tracker and no cached hunks gives [NeedsBaseContent].
   */
  private fun readHunks(file: VirtualFile, baseRevision: VcsRevisionNumber, changeListId: String?, baseContent: String?): HunkRead? {
    if (DumbService.isDumb(project) || !file.isValid) return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val partialCommit = LineStatusTrackerManager.getInstanceImpl(project).canCreatePartialTracker(file, document)
    // The ranges of a valid tracker are the ones that the commit uses.
    val trackerDiff = PartialChangesUtil.getPartialTracker(project, file)?.takeIf { it.isValid() }?.let { readTracker(it, changeListId) }
    val version = FileVersion(document.modificationStamp, baseRevision.asString(), trackerDiff?.trackerBaseHash)
    val baseContentHash = if (baseRevision == VcsRevisionNumber.NULL) baseContent?.let { StringUtil.stringHashCode(it) } else null
    val source = HunkSource(file, changeListId, version, baseContentHash, trackerDiff?.hunks)
    hunkCache[source]?.let { return FileHunks(source, it, partialCommit) }

    val diff = trackerDiff ?: compareLines(file, document, baseContent ?: return NeedsBaseContent)
    if (diff.currentText.length > MAX_CONTENT_LENGTH) return null
    val original = PsiManager.getInstance(project).findFile(file) ?: return null
    val before = StructuralHunkNamer.createSnapshot(original, diff.baseText) ?: return null
    val after = StructuralHunkNamer.createSnapshot(original, diff.currentText) ?: return null
    val names = StructuralHunkNamer.name(before, after, diff.hunks)
    val hunks = diff.hunks.zip(names) { lines, name -> Hunk(lines, name, version) }
    hunkCache[source] = hunks
    return FileHunks(source, hunks, partialCommit)
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

  private fun <T> readNonBlocking(compute: () -> T): T = ReadAction.nonBlocking(Callable { compute() }).expireWith(project).executeSynchronously()

  /** The hunks of a file. A diff of a tracker has the hash of the tracker base text. */
  private class FileDiff(val baseText: String, val currentText: String, val hunks: List<HunkLines>, val trackerBaseHash: Int?)

  /** A read of the hunks of a file: the hunks, or the request for the base content. */
  private sealed interface HunkRead

  private class FileHunks(val source: HunkSource, val hunks: List<Hunk>, val partialCommit: Boolean) : HunkRead

  /** The hunks need the base content. The caller loads it outside the read action and reads again. */
  private data object NeedsBaseContent : HunkRead

  private companion object {
    const val MAX_CONTENT_LENGTH = 1_000_000
    const val MAX_FILES_KEY = "vcs.show.structural.changes.max.files"
  }
}

private class Hunk(val lines: HunkLines, val name: HunkName, val version: FileVersion)

/**
 * The document version and the base that the hunks of a file describe. The tracker base text identifies the base of the hunks of a tracker.
 * Other hunks use the base revision, because a new tracker loads the base of the current change.
 */
private data class FileVersion(val documentModificationStamp: Long, val baseRevision: String, val trackerBaseHash: Int?)

/**
 * The inputs of the hunks of a file. A tree rebuild reuses the hunks while the inputs stay the same. A revision number identifies the base
 * content, so the inputs hold a hash of the base content only for a base without a number. The ranges of a tracker can move to another
 * changelist, so they are part of the inputs.
 */
private data class HunkSource(
  val file: VirtualFile,
  val changeListId: String?,
  val version: FileVersion,
  val baseContentHash: Int?,
  val trackerLines: List<HunkLines>?,
)

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
  private val hunk: Hunk,
  partialCommit: Boolean,
) : ChangesBrowserStructuralNode<HunkKey>(HunkKey(path, changeListId, hunk.lines), hasInclusion = partialCommit) {
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

  override fun getTextPresentation(): String {
    val parts = hunk.name.parts
    if (parts.isEmpty()) return VcsBundle.message("changes.structural.blank.lines")
    val shown = shownPartCount()
    val text = joinedParts(parts.take(shown))
    return if (shown < parts.size) text + " " + VcsBundle.message("changes.structural.more.parts", parts.size - shown) else text
  }

  /**
   * The number of parts in the row: the parts that fit [MAX_ROW_LENGTH] characters, and at least one. A hunk can change hundreds of
   * elements, as a rename of many methods does, so the row gives the count of the other parts.
   */
  private fun shownPartCount(): Int {
    var length = 0
    hunk.name.parts.forEachIndexed { index, part ->
      length += partText(part).length + if (index > 0) SEPARATOR.length else 0
      if (index > 0 && length > MAX_ROW_LENGTH) return index
    }
    return hunk.name.parts.size
  }

  /** The parts are source text, and the separators are symbols, so the text needs no translation. */
  private fun joinedParts(parts: List<HunkNamePart>): @NlsSafe String = parts.joinToString(SEPARATOR, transform = ::partText)

  private fun partText(part: HunkNamePart): @NlsSafe String = part.previousText?.let { "$it → ${part.text}" } ?: part.text

  /** Lists the parts one per line, at most [MAX_TOOLTIP_PARTS] of them, and then the detail. */
  private fun tooltip(): @Nls String {
    val parts = hunk.name.parts
    val builder = HtmlBuilder()
    if (parts.isEmpty()) builder.append(VcsBundle.message("changes.structural.blank.lines"))
    parts.take(MAX_TOOLTIP_PARTS).forEachIndexed { index, part ->
      if (index > 0) builder.br()
      builder.append(partText(part))
    }
    if (parts.size > MAX_TOOLTIP_PARTS) {
      builder.br().append(VcsBundle.message("changes.structural.more.parts", parts.size - MAX_TOOLTIP_PARTS))
    }
    hunk.name.detail?.let { builder.br().append(it.text) }
    return builder.wrapWithHtmlBody().toString()
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val name = hunk.name
    renderer.icon = name.icon ?: EmptyIcon.ICON_16
    if (name.parts.isEmpty()) {
      renderer.append(VcsBundle.message("changes.structural.blank.lines"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      return
    }
    val shown = shownPartCount()
    name.parts.take(shown).forEachIndexed { index, part ->
      if (index > 0) renderer.append(SEPARATOR, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      part.previousText?.let {
        renderer.append(it, attributes(FileStatus.DELETED))
        renderer.append(" → ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      renderer.append(part.text, attributes(part.status))
    }
    if (shown < name.parts.size) {
      renderer.append(" " + VcsBundle.message("changes.structural.more.parts", name.parts.size - shown), SimpleTextAttributes.GRAYED_ATTRIBUTES)
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
    const val MAX_ROW_LENGTH = 80
    const val MAX_TOOLTIP_PARTS = 20
    const val SEPARATOR = ", "
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
