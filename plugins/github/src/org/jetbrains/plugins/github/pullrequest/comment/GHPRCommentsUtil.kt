// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffRangeMapping
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping

object GHPRCommentsUtil {
  val LOG = logger<GHPRCommentsUtil>()

  /**
   * For file comments GitHub provides 5 key values:   *
   *    originalCommitId - commit SHA of the last commit when the comment was created
   *    commitId - SHA of the current last commit
   *
   *    originalPosition - line INDEX in diff between PR base and commit "originalCommitId"
   *    position - line index in diff between PR base and commit "commitId" or [null] if comment is obsolete (change is overwritten)
   *
   *    path - relative file path after commit "originalCommitId"
   *
   * We use this info to map comments to file line number and change side:
   *  1. Build reviewThreads from comments to reduce amount of work (so we only have to do it for the first comment in thread)
   *  2. Find revision range for file using list of commits, "originalCommitId" and "path" (we need to analyze commit chain to track renames)
   *    a. Find commit "originalCommitId"
   *    b. Walk up and down list of commits from "originalCommitId" to find first and last known revisions of that particular file
   *    c. Combine revisions to get revision range
   *  3. Extract diff for single file from cumulative diff using data from step 2
   *  4. Map "position" to diff hunk and calculate file line and change side using hunk data
   */
  fun buildThreadsAndMapLines(repository: GitRepository,
                              commits: List<GitCommit>,
                              diffFile: String,
                              reviewThreads: List<GHPullRequestReviewThread>): Map<Change, List<GHPRDiffReviewThreadMapping>> {

    val patchReader = PatchReader(diffFile, true)
    patchReader.parseAllPatches()

    val fileChangesFinder = FileChangesTracker(commits)

    val threadsByChange = mutableMapOf<Change, MutableList<GHPullRequestReviewThread>>()
    for (thread in reviewThreads.filter { it.position != null }) {
      val commitSha = thread.originalCommit?.oid
      if (commitSha == null) {
        LOG.debug("Missing original commit sha for thread $thread")
        continue
      }
      val filePath = VcsUtil.getFilePath(repository.root, GitUtil.unescapePath(thread.path))
      val change = fileChangesFinder.traceAndCollectFileChanges(commitSha, filePath)
      threadsByChange.getOrPut(change, ::mutableListOf).add(thread)
    }

    val threadMappingsByChange = mutableMapOf<Change, List<GHPRDiffReviewThreadMapping>>()
    for ((change, threads) in threadsByChange) {
      val diff = findDiffForChange(repository, patchReader, change)
      if (diff == null) {
        LOG.debug("Missing diff for change $change")
        continue
      }

      val mappings = mutableListOf<GHPRDiffReviewThreadMapping>()
      for (thread in threads) {
        val diffLineIndex = thread.position!!
        val mapping = calculateLineLocation(diff, diffLineIndex)?.let { (side, lineIndex) ->
          GHPRDiffReviewThreadMapping(side, lineIndex, thread)
        }
        if (mapping == null) {
          LOG.debug("Invalid position $diffLineIndex\nin diff $diff")
          continue
        }
        mappings.add(mapping)
      }
      if (mappings.isNotEmpty()) threadMappingsByChange[change] = mappings
    }
    return threadMappingsByChange
  }

  private fun findDiffForChange(repository: GitRepository, patchReader: PatchReader, change: Change): TextFilePatch? {
    val beforePath = change.beforeRevision?.let { VcsFileUtil.relativePath(repository.root, it.file) }
    val afterPath = change.afterRevision?.let { VcsFileUtil.relativePath(repository.root, it.file) }

    return patchReader.textPatches.find {
      it.beforeName == beforePath && it.isDeletedFile ||
      it.afterName == afterPath && it.isNewFile ||
      it.beforeName == beforePath && it.afterName == afterPath
    }
  }

  private fun calculateLineLocation(diff: TextFilePatch, diffLineIndex: Int): Pair<Side, Int>? {
    var diffLineCounter = 0
    for (hunk in diff.hunks) {
      val hunkLinesCount = getHunkLinesCount(hunk)
      diffLineCounter += hunkLinesCount

      if (diffLineIndex < diffLineCounter) {
        // -1 for missing header
        val hunkLineIndex = diffLineIndex - (diffLineCounter - hunkLinesCount) - 1

        val line = hunk.lines[hunkLineIndex] ?: return null
        return when (line.type!!) {
          PatchLine.Type.REMOVE -> {
            val addedLinesBefore = hunk.lines.subList(0, hunkLineIndex).count { it.type == PatchLine.Type.ADD }
            Side.LEFT to hunk.startLineBefore + (hunkLineIndex - addedLinesBefore)
          }
          PatchLine.Type.CONTEXT, PatchLine.Type.ADD -> {
            val removedLinesBefore = hunk.lines.subList(0, hunkLineIndex).count { it.type == PatchLine.Type.REMOVE }
            Side.RIGHT to hunk.startLineAfter + (hunkLineIndex - removedLinesBefore)
          }
        }
      }
    }
    return null
  }

  // +1 for header
  private fun getHunkLinesCount(hunk: PatchHunk) = hunk.lines.size + hunk.lines.count { it.isSuppressNewLine } + 1

  fun getDiffRangesMapping(project: Project, repository: GitRepository, lastCommitSha: String, diffFile: String)
    : Map<Change, List<GHPRDiffRangeMapping>> {

    val patchReader = PatchReader(diffFile, true)
    patchReader.parseAllPatches()

    val map = mutableMapOf<Change, List<GHPRDiffRangeMapping>>()
    val patches = patchReader.textPatches
    for (diff in patches) {
      val change = createChangeFromDiff(project, repository, diff)
      val mappings = mutableListOf<GHPRDiffRangeMapping>()
      val filePath = diff.afterName

      //first hunk offset is 1 because comment on header is not possible
      var hunkOffset = 1
      for (hunk in diff.hunks) {
        val leftSide = MappingsCollector(lastCommitSha, filePath, Side.LEFT, hunk.startLineBefore, hunkOffset)
        val rightSide = MappingsCollector(lastCommitSha, filePath, Side.RIGHT, hunk.startLineAfter, hunkOffset)

        var lastLineType: PatchLine.Type? = null
        for (line in hunk.lines) {
          val lineType = line.type!!
          if (lastLineType != null && lastLineType != lineType) {
            leftSide.saveMapping()
            rightSide.saveMapping()
          }
          lastLineType = lineType

          when (lineType) {
            PatchLine.Type.CONTEXT -> {
              leftSide.addLine()
              rightSide.addLine()
            }
            PatchLine.Type.ADD -> {
              leftSide.addOffset()
              rightSide.addLine()
            }
            PatchLine.Type.REMOVE -> {
              leftSide.addLine()
              rightSide.addOffset()
            }
          }
        }
        leftSide.saveMapping()
        rightSide.saveMapping()

        mappings.addAll(leftSide.mappings)
        mappings.addAll(rightSide.mappings)

        hunkOffset += getHunkLinesCount(hunk)
      }
      map[change] = mappings
    }
    return map
  }

  private class MappingsCollector(private val commitSha: String, private val filePath: String, private val side: Side,
                                  startLine: Int, hunkOffset: Int) {
    val mappings = mutableListOf<GHPRDiffRangeMapping>()

    private var start = startLine
    private var end = start
    private var offset = 0 - startLine + hunkOffset

    fun addLine() {
      end++
    }

    fun addOffset() {
      offset++
    }

    fun saveMapping() {
      if (start == end) return
      mappings.add(GHPRDiffRangeMapping(commitSha, filePath, side, start, end, offset))
      start = end
    }
  }

  private fun createChangeFromDiff(project: Project, repository: GitRepository, diff: TextFilePatch): Change {
    val beforePath = VcsUtil.getFilePath(repository.root, GitUtil.unescapePath(diff.beforeName))
    val afterPath = VcsUtil.getFilePath(repository.root, GitUtil.unescapePath(diff.afterName))

    val beforeRev = GitRevisionNumber(diff.beforeVersionId)
    val afterRev = GitRevisionNumber(diff.afterVersionId)

    return Change(if (diff.isNewFile) null else GitContentRevision.createRevision(beforePath, beforeRev, project),
                  if (diff.isDeletedFile) null else GitContentRevision.createRevision(afterPath, afterRev, project))
  }

  private class FileChangesTracker(private val commits: List<GitCommit>) {
    private val cache = mutableMapOf<Pair<String, FilePath>, Change>()

    @Throws(IllegalStateException::class)
    fun traceAndCollectFileChanges(commitSha: String, filePath: FilePath): Change {
      return cache.getOrElse(commitSha to filePath) {
        doTraceAndCollectChanges(commitSha, filePath)
      }
    }

    private fun doTraceAndCollectChanges(commitSha: String, filePath: FilePath): Change {
      val commitIndex = findCommitIndex(commitSha)
      var referencedChange: Change? = null
      for (commit in commits.subList(0, commitIndex + 1).asReversed()) {
        val change = commit.changes.find { ChangesUtil.isAffectedByChange(filePath, it) }
        if (change != null) referencedChange = change
      }
      if (referencedChange == null) throw IllegalStateException("Can't find file $filePath change in ${commitSha}")

      val revisionChain = mutableListOf<ContentRevision?>()

      revisionChain.add(referencedChange.beforeRevision)
      buildRevisionChainHead(revisionChain, commits.subList(0, commitIndex))

      revisionChain.add(referencedChange.afterRevision)
      buildRevisionChainTail(revisionChain, commits.subList(commitIndex + 1, commits.size))

      val summaryChange = Change(revisionChain.first(), revisionChain.last())
      for (revision in revisionChain) {
        if (revision != null) {
          cache[revision.revisionNumber.asString() to revision.file] = summaryChange
        }
      }
      return summaryChange
    }

    /**
     * Walk [commits] list in reversed order until list start or revision chain start (null revision)
     */
    private fun buildRevisionChainHead(chain: MutableList<ContentRevision?>, commits: List<GitCommit>) {
      for (commit in commits.asReversed()) {
        val lastKnownFilePath = chain.first()?.file ?: return

        val change = commit.changes.find { it.afterRevision?.file == lastKnownFilePath } ?: continue
        chain.add(0, change.beforeRevision)
      }
    }

    /**
     * Walk [commits] list until list end or revision chain end (null revision)
     */
    private fun buildRevisionChainTail(chain: MutableList<ContentRevision?>, commits: List<GitCommit>) {
      for (commit in commits) {
        val lastKnownFilePath = chain.last()?.file ?: return

        val change = commit.changes.find { lastKnownFilePath == it.beforeRevision?.file } ?: continue
        chain.add(change.afterRevision)
      }
    }

    private fun findCommitIndex(commitSha: String): Int {
      val index = commits.indexOfFirst { it.id.asString() == commitSha }
      if (index < 0) throw IllegalStateException("Can't find commit ${commitSha}")
      return index
    }
  }
}