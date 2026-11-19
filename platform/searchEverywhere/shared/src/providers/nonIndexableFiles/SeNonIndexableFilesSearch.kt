// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.nonIndexableFiles

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.DirectorySearchEngine.FileSearchCandidate
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.NonIndexableProductBehaviorService
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.ConcurrentFileTraversal
import com.intellij.util.text.matching.MatchingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Finds the non-indexable files and directories of a project by name or by path.
 *
 * It computes no presentation. [SeNonIndexableFilesProvider] makes the Search Everywhere items from the found files.
 */
@ApiStatus.Internal
class SeNonIndexableFilesSearch(private val project: Project) {
  /**
   * Finds the items that match [pattern] and sends each item to [consumer] with its matching degree.
   *
   * An absolute path or a project-relative path in [pattern] comes first, with the exact match degree.
   * The good name matches come next, and the other matches come last.
   * The search runs many short read actions instead of one long read action.
   *
   * @param scope the scope of a found file. With `null`, the search checks no scope and also searches in the libraries.
   * @param hiddenTypes the file types that the search leaves out.
   * @param consumer gets an item without the read lock. It returns `false` to stop the search.
   */
  suspend fun search(
    pattern: String,
    scope: SearchScope?,
    hiddenTypes: Set<FileTypeRef>,
    consumer: suspend (item: PsiFileSystemItem, matchingDegree: Int) -> Boolean,
  ) {
    val searchInLibraries = (scope as? GlobalSearchScope)?.isSearchInLibraries ?: true
    if (!searchInLibraries && !Registry.`is`("lookup.non.indexable.content.in.project.scope")) return
    if (!NonIndexableProductBehaviorService.getInstance().shouldLookupInProjectScopes() && !searchInLibraries) return

    val [namePattern, pathPattern] = run {
      val sanitizedPattern = GotoFileItemProvider.getSanitizedPattern(pattern, null).removePrefix("*")
      sanitizedPattern.substringAfterLast('/').removePrefix("*") to sanitizedPattern
    }

    val pathMatcher = GotoFileItemProvider.getQualifiedNameMatcher(pathPattern)
    val nameMatcher = NameUtil.buildMatcher("*$namePattern")
      .withMatchingMode(MatchingMode.IGNORE_CASE)
      .preferringStartMatches()
      .build()
    val directorySearchEngines = DirectorySearchEngine.EP_NAME.extensionList.filter { it.canSearchNames() }

    // The consumer can take a limited number of items.
    // So the search sends the good matches first, and sends the other matches only when it did not find enough.
    val suboptimalMatches = ConcurrentLinkedQueue<FileSearchCandidate>()

    val filterByType = VirtualFileFilter { file -> FileTypeRef.forFileType(file.fileType) !in hiddenTypes }
    val filter: VirtualFileFilter = if (scope == null) filterByType
    else filterByType.and { file -> scope.containsNonIndexed(file) }

    val psiManager = PsiManager.getInstance(project)

    coroutineScope {
      // A path pattern is an explicit navigation request, as in the index-based Files provider.
      // A product without that provider gets the lookup here; with it, Search Everywhere merges the equal items.
      val pathItem = readActionUndispatched { GotoFileItemProvider.findFileSystemItemByAbsolutePath(project, pattern) }
      if (pathItem != null && !consumer(pathItem, DefaultChooseByNameItemProvider.EXACT_MATCH_DEGREE)) return@coroutineScope

      // we do not pass [filter] here, because we want to show files that do not match the filter in suboptimal matches
      val nonIndexableTraversal = readAction { ConcurrentFileTraversal.nonIndexableTraversal(project, searchInLibraries) }
      val state = SearchJobsState(nonIndexableTraversal)
      if (state.roots.isEmpty()) return@coroutineScope

      val toplevelProducerJob = launch(Dispatchers.IO.limitedParallelism(MAX_JOBS)) {
        ParallelQueueProcessor.createRunning(
          scope = this@launch, jobsNumber = MAX_JOBS, initialItems = state.roots, workerJobYieldTimeout = 50.milliseconds
        ) { handle, item ->
          fun processResult(file: FileSearchCandidate) {
            val pathFromNonIndexableRoot = state.getPathFromRoot(file)
            if (pathFromNonIndexableRoot == null) {
              LOG.warn("File $file that was yielded as a file under a non-indexable root didn't match any non-indexable roots; Continue search...")
              return
            }

            if (pathMatcher.matches(pathFromNonIndexableRoot)) {
              val matchingDegree = nameMatcher.matchingDegree(file.name)
              if (matchingDegree <= 0) {
                // suboptimal match, process later, after "optimal" matches
                suboptimalMatches.add(file)
              }
              else {
                state.emitResult(file, matchingDegree)
              }
            } // else - file doesn't match pattern, skip
          }

          val bestEngine = when {
            directorySearchEngines.isEmpty() -> null
            !item.getSubtreeProcessingMode().shouldProcessChildren -> null
            else -> DirectorySearchEngine.selectDirectorySearchEngine(item.file, directorySearchEngines)
          }

          val handled: Boolean = try {
            bestEngine?.searchNames(item.file, pathPattern, ::processResult) != null
          }
          catch (_: Throwable) {
            checkCanceled()
            false
          }

          if (!handled) {
            val shouldProcessSelf = state.processItem(item, handle)
            if (shouldProcessSelf) {
              processResult(FileSearchCandidate.fromVirtualFile(item.file))
            }
          }
        }
      }

      toplevelProducerJob.invokeOnCompletion {
        state.producerCompleted()
      }

      val consumerStopped = !state.collectResults { file, matchingDegree ->
        val psiItem = readActionUndispatched {
          if (filter.accept(file)) findFileSystemItem(psiManager, file) else null
        }
        if (psiItem == null || psiItem == pathItem) return@collectResults true

        val consumed = consumer(psiItem, matchingDegree)
        if (!consumed) {
          toplevelProducerJob.cancel("consumer stopped")
        }
        consumed
      }

      if (consumerStopped) return@coroutineScope

      if (suboptimalMatches.isEmpty() || namePattern.length < 2) return@coroutineScope

      val otherNameMatchers = List(namePattern.length - 1) { i ->
        NameUtil.buildMatcher(" " + namePattern.substring(i + 1))
          .withMatchingMode(MatchingMode.IGNORE_CASE)
          .build()
      }

      for (candidate in suboptimalMatches) {
        // binary search instead of linear?
        for (i in otherNameMatchers.indices) {
          val matchingDegree = otherNameMatchers[i].matchingDegree(candidate.name)
          if (matchingDegree <= 0) continue

          val file = candidate.resolveVirtualFile() ?: break
          // These locks slow the throughput less than the locks in the producerJob iteration
          // because these are really just file leftovers
          val psiItem = readActionUndispatched { findFileSystemItem(psiManager, file) }
          if (psiItem != null && psiItem != pathItem) {
            val weight = matchingDegree * (otherNameMatchers.size - i) / (otherNameMatchers.size + 1)
            if (!consumer(psiItem, weight)) return@coroutineScope
          }
          break
        }
      }
    }
  }

  @RequiresReadLock
  private fun findFileSystemItem(psiManager: PsiManager, file: VirtualFile): PsiFileSystemItem? =
    if (file.isDirectory) psiManager.findDirectory(file) else psiManager.findFile(file)

  private companion object {
    private val LOG = logger<SeNonIndexableFilesSearch>()

    /** How many jobs walk the roots at once. */
    private const val MAX_JOBS = 3
  }
}
