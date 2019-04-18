// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.GitUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.comment.model.GithubPullRequestFileCommentThread

object GithubPullRequestCommentsUtil {

  fun buildThreadsAndMapLines(repository: GitRepository,
                              commits: List<GitCommit>,
                              diffFile: String,
                              allComments: List<GithubPullRequestCommentWithHtml>): List<GithubPullRequestFileCommentThread> {

    val actualFileComments = allComments.filter { it.path != null && it.position != null }
    val rawThreads = buildThreads(actualFileComments)

    val threadsByChange = rawThreads.groupBy { comments ->
      val root = comments.first()

      val commitIndex = commits.indexOfFirst { it.id.asString() == root.originalCommitId }
      if (commitIndex < 0) throw IllegalStateException("Can't find commit ${root.originalCommitId}")

      val filePath: FilePath = VcsUtil.getFilePath(repository.root, GitUtil.unescapePath(root.path!!))
      var firstChange: Change = commits[commitIndex].let { commit -> commit.changes.find { it.affectsFile(filePath) } }
                                ?: throw IllegalStateException("Can't find file $filePath change in ${root.originalCommitId}")

      var lastChange: Change = firstChange

      if (commitIndex > 0 && firstChange.beforeRevision != null)
        for (i in (commitIndex - 1) downTo 0) {
          val change = commits[i].changes.find { it.afterRevision?.file == firstChange.beforeRevision!!.file } ?: continue
          firstChange = change
          if (firstChange.beforeRevision == null) break
        }

      if (commitIndex < commits.size - 1 && lastChange.afterRevision != null)
        for (i in commitIndex + 1 until commits.size) {
          val change = commits[i].changes.find { lastChange.afterRevision!!.file == it.beforeRevision?.file } ?: continue
          lastChange = change
          if (lastChange.afterRevision == null) break
        }

      if (firstChange == lastChange) firstChange
      else Change(firstChange.beforeRevision, lastChange.afterRevision)
    }


    val patchReader = PatchReader(diffFile, true)
    patchReader.parseAllPatches()
    val patches = patchReader.textPatches

    return threadsByChange.map { (change, threads) ->
      val beforePath = change.beforeRevision?.let { VcsFileUtil.relativePath(repository.root, it.file) }
      val afterPath = change.afterRevision?.let { VcsFileUtil.relativePath(repository.root, it.file) }

      val patch = patches.find {
        it.beforeName == beforePath && it.isDeletedFile ||
        it.afterName == afterPath && it.isNewFile ||
        it.beforeName == beforePath && it.afterName == afterPath
      } ?: throw IllegalStateException("Missing diff for change $change")

      val hunksWithSizes = patch.hunks.map {
        it to (it.lines.size + 1 + it.lines.count { it.isSuppressNewLine })
      }

      threads.map { comments ->
        val diffLineNumber = comments.first().position!!.toInt() + 1
        calculateLocation(hunksWithSizes, diffLineNumber)?.let { (side, line) ->
          GithubPullRequestFileCommentThread(change, side, line, comments)
        }
      }
    }.flatten().filterNotNull()
  }

  private fun buildThreads(actualFileComments: List<GithubPullRequestCommentWithHtml>): Collection<List<GithubPullRequestCommentWithHtml>> {
    val commentsByThreadRootId = actualFileComments.groupBy { it.inReplyToId }.toMutableMap()

    val threadedComments = commentsByThreadRootId.remove(null)?.map { it.id to mutableListOf(it) }?.toMap() ?: return emptyList()
    for ((rootId, comments) in commentsByThreadRootId) {
      threadedComments[rootId]?.addAll(comments)
    }
    return threadedComments.values
  }

  private fun calculateLocation(hunksWithSizes: List<Pair<PatchHunk, Int>>, diffLineNumber: Int): Pair<Side, Int>? {
    var diffLineCounter = 0
    for ((hunk, size) in hunksWithSizes) {
      diffLineCounter += size
      if (diffLineNumber <= diffLineCounter) {
        //-2 because compensation for missing header and it's not PHP (arrays start from 0)
        val hunkLineIndex = diffLineNumber - (diffLineCounter - size) - 2
        val line = hunk.lines[hunkLineIndex]
        when (line?.type) {
          PatchLine.Type.CONTEXT, PatchLine.Type.REMOVE -> {
            return Side.LEFT to hunk.startLineBefore + hunkLineIndex + 1
          }
          PatchLine.Type.ADD -> {
            return Side.RIGHT to hunk.startLineAfter + (hunkLineIndex - hunk.lines.count { it.type == PatchLine.Type.REMOVE }) + 1
          }
        }
        break
      }
    }
    return null
  }
}