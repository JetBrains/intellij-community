// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.lang.changes

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.HunkLines
import com.intellij.openapi.vcs.changes.HunkName
import com.intellij.openapi.vcs.changes.StructuralHunkNamer
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Computes the hunks of a modified file and names each hunk by the elements of the file structure that it changes. A cache keeps the hunks
 * of a file while its inputs stay the same, so that a second read names only the files whose inputs changed. A file that no read used for
 * five minutes leaves the cache, so the cache holds no hunks while no tree shows them.
 *
 * Call it outside the event thread: a base content can come from a VCS process.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class StructuralHunksService(private val project: Project, coroutineScope: CoroutineScope) {
  private val cache = HunkCache(coroutineScope)

  /**
   * The hunks of a local change of [file], and whether the commit can include them one by one. The ranges of a valid partial tracker win,
   * so that the hunks are the ones that the commit uses. With a [changeListId], only the ranges of that changelist count. Returns null in
   * dumb mode, and for a file without a document or a PSI file, or above the size limit.
   */
  fun localChangeHunks(file: VirtualFile, beforeRevision: ContentRevision, changeListId: String?): StructuralFileHunks? =
    collect(file, beforeRevision, changeListId, useTracker = true)

  /**
   * The hunks between [beforeRevision] and the current text of [file]. No tracker takes part, so [beforeRevision] can be any revision, such
   * as a commit below HEAD. Returns null in the same cases as [localChangeHunks].
   */
  fun hunks(file: VirtualFile, beforeRevision: ContentRevision): List<StructuralHunk>? =
    collect(file, beforeRevision, changeListId = null, useTracker = false)?.hunks

  /** Removes the hunks of each file that no read used for [age], as the cache does for its files after [HUNKS_LIFETIME]. */
  @TestOnly
  fun removeHunksUnreadFor(age: Duration) {
    cache.removeUnreadFor(age)
  }

  /**
   * The cache or a tracker gives the hunks without the base content. A load of the base content can take long, so it runs outside a read
   * action and only when the hunks need it.
   */
  private fun collect(
    file: VirtualFile,
    beforeRevision: ContentRevision,
    changeListId: String?,
    useTracker: Boolean,
  ): StructuralFileHunks? {
    val baseRevision = beforeRevision.revisionNumber
    // A revision number identifies the base content, so only a base without a number needs its content to find the cached hunks.
    if (baseRevision != VcsRevisionNumber.NULL) {
      val read = readNonBlocking { read(file, baseRevision, changeListId, useTracker, baseContent = null) }
      if (read != NeedsBaseContent) return (read as? Hunks)?.value
    }
    val baseContent = beforeRevision.content ?: return null
    if (baseContent.length > MAX_CONTENT_LENGTH) return null
    return (readNonBlocking { read(file, baseRevision, changeListId, useTracker, baseContent) } as? Hunks)?.value
  }

  /**
   * Reads the hunks of a file. The commit can include them one by one only with a partial tracker, and only some VCSs, settings, and file
   * statuses give one. Without [baseContent], a file that has no tracker and no cached hunks gives [NeedsBaseContent].
   */
  private fun read(
    file: VirtualFile,
    baseRevision: VcsRevisionNumber,
    changeListId: String?,
    useTracker: Boolean,
    baseContent: String?,
  ): HunkRead? {
    if (DumbService.isDumb(project) || !file.isValid) return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val partialCommit = useTracker && LineStatusTrackerManager.getInstanceImpl(project).canCreatePartialTracker(file, document)
    // The ranges of a valid tracker are the ones that the commit uses.
    val tracker = if (useTracker) PartialChangesUtil.getPartialTracker(project, file)?.takeIf { it.isValid() } else null
    val trackerDiff = tracker?.let { readTracker(it, changeListId) }
    val version = StructuralHunkVersion(document.modificationStamp, baseRevision.asString(), trackerDiff?.trackerBaseHash)
    val baseContentHash = if (baseRevision == VcsRevisionNumber.NULL) baseContent?.let { StringUtil.stringHashCode(it) } else null
    // The cache holds the file. A file outside the local file system, such as a file of a revision, can hold its content in memory.
    val key = CacheKey(file, changeListId, version.baseRevision, useTracker).takeIf { file.isInLocalFileSystem }
    val source = HunkSource(version, baseContentHash, trackerDiff?.hunks)
    key?.let { cache.get(it, source) }?.let { return Hunks(StructuralFileHunks(it, partialCommit)) }

    val diff = trackerDiff ?: compareLines(file, document, baseContent ?: return NeedsBaseContent)
    if (diff.currentText.length > MAX_CONTENT_LENGTH) return null
    val original = PsiManager.getInstance(project).findFile(file) ?: return null
    val before = StructuralHunkNamer.createSnapshot(original, diff.baseText) ?: return null
    val after = StructuralHunkNamer.createSnapshot(original, diff.currentText) ?: return null
    val names = StructuralHunkNamer.name(before, after, diff.hunks)
    val hunks = diff.hunks.zip(names) { lines, name -> StructuralHunk(lines, name, version) }
    key?.let { cache.put(it, source, hunks) }
    return Hunks(StructuralFileHunks(hunks, partialCommit))
  }

  private fun readTracker(tracker: PartialLocalLineStatusTracker, changeListId: String?): FileDiff? = tracker.readLock {
    val ranges = tracker.getRanges() ?: return@readLock null
    val baseText = tracker.vcsDocument.immutableCharSequence.toString()
    val lines = ranges.filter { changeListId == null || it.changelistId == changeListId }
      .map { HunkLines(it.vcsLine1, it.vcsLine2, it.line1, it.line2) }
    FileDiff(baseText, tracker.document.immutableCharSequence.toString(), lines, StringUtil.stringHashCode(baseText))
  }

  private fun compareLines(file: VirtualFile, document: Document, baseContent: String): FileDiff {
    val baseText = StringUtil.convertLineSeparators(TextPresentationTransformers.fromPersistent(baseContent, file).toString())
    val currentText = document.immutableCharSequence.toString()
    val indicator = ProgressManager.getInstance().progressIndicator ?: DumbProgressIndicator.INSTANCE
    val fragments = ComparisonManager.getInstance().compareLines(baseText, currentText, ComparisonPolicy.DEFAULT, indicator)
    return FileDiff(baseText, currentText, fragments.map { HunkLines(it.startLine1, it.endLine1, it.startLine2, it.endLine2) }, null)
  }

  private fun <T> readNonBlocking(compute: () -> T): T =
    ReadAction.nonBlocking(Callable { compute() }).expireWith(project).executeSynchronously()

  /** The hunks of a file. A diff of a tracker has the hash of the tracker base text. */
  private class FileDiff(val baseText: String, val currentText: String, val hunks: List<HunkLines>, val trackerBaseHash: Int?)

  /** A read of the hunks of a file: the hunks, or the request for the base content. */
  private sealed interface HunkRead

  private class Hunks(val value: StructuralFileHunks) : HunkRead

  /** The hunks need the base content. The caller loads it outside the read action and reads again. */
  private data object NeedsBaseContent : HunkRead

  companion object {
    private const val MAX_CONTENT_LENGTH = 1_000_000

    fun getInstance(project: Project): StructuralHunksService = project.service()
  }
}

/** A hunk of a modified file: its lines on both sides, its name, and the version of the file that the lines describe. */
@ApiStatus.Internal
class StructuralHunk(val lines: HunkLines, val name: HunkName, val version: StructuralHunkVersion)

/**
 * The document version and the base that the hunks of a file describe. The tracker base text identifies the base of the hunks of a tracker.
 * Other hunks use the base revision, because a new tracker loads the base of the current change.
 */
@ApiStatus.Internal
data class StructuralHunkVersion(val documentModificationStamp: Long, val baseRevision: String, val trackerBaseHash: Int?)

/** The hunks of a local change, and whether the commit can include them one by one. */
@ApiStatus.Internal
class StructuralFileHunks(val hunks: List<StructuralHunk>, val partialCommit: Boolean)

/**
 * One entry of the cache for each file, changelist, and base revision. The hunks of a local change and the hunks against any base have
 * different entries, so that a tree of each kind keeps its hunks while the other tree reads the same file.
 */
private data class CacheKey(val file: VirtualFile, val changeListId: String?, val baseRevision: String, val useTracker: Boolean)

/**
 * The inputs of the hunks of a file. A read reuses the hunks while the inputs stay the same. A revision number identifies the base content,
 * so the inputs hold a hash of the base content only for a base without a number. The ranges of a tracker can move to another changelist,
 * so they are part of the inputs.
 */
private data class HunkSource(val version: StructuralHunkVersion, val baseContentHash: Int?, val trackerLines: List<HunkLines>?)

/**
 * Keeps the hunks of the files that the trees read last. The least recently read file leaves first. A file that no read used for
 * [HUNKS_LIFETIME] leaves too: a sweep in [scope] removes it. The sweep runs only while the cache holds a file.
 */
private class HunkCache(private val scope: CoroutineScope) {
  private val entries = LinkedHashMap<CacheKey, HunkEntry>(16, 0.75f, true)

  /** The sweep of the unread files, or null while the cache is empty. */
  private var sweep: Job? = null

  @Synchronized
  fun get(key: CacheKey, source: HunkSource): List<StructuralHunk>? {
    val entry = entries[key]?.takeIf { it.source == source } ?: return null
    entry.readAt = System.nanoTime()
    return entry.hunks
  }

  @Synchronized
  fun put(key: CacheKey, source: HunkSource, hunks: List<StructuralHunk>) {
    entries[key] = HunkEntry(source, hunks, System.nanoTime())
    if (entries.size > MAX_CACHED_FILES) entries.remove(entries.keys.first())
    if (sweep == null) sweep = scope.launch { sweepUnread() }
  }

  /** Removes each file that no read used for [age]. */
  @Synchronized
  fun removeUnreadFor(age: Duration) {
    val oldestRead = System.nanoTime() - age.inWholeNanoseconds
    entries.values.removeIf { it.readAt <= oldestRead }
  }

  private suspend fun sweepUnread() {
    while (true) {
      delay(HUNKS_LIFETIME)
      if (removeUnreadAndStopWhenEmpty()) return
    }
  }

  /** The sweep stops in the same lock as [put] starts it, so each new file of an empty cache starts a sweep. */
  @Synchronized
  private fun removeUnreadAndStopWhenEmpty(): Boolean {
    removeUnreadFor(HUNKS_LIFETIME)
    if (entries.isNotEmpty()) return false
    sweep = null
    return true
  }

  private companion object {
    const val MAX_CACHED_FILES = 1_000
  }
}

/** The hunks of a file, the inputs that they describe, and the time of the last read, from [System.nanoTime]. */
private class HunkEntry(val source: HunkSource, val hunks: List<StructuralHunk>, var readAt: Long)

/** How long the cache keeps the hunks of a file that no read uses. */
private val HUNKS_LIFETIME: Duration = 5.minutes
