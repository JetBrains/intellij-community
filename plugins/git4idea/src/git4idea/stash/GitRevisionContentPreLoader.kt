// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber.HEAD
import git4idea.GitVcs
import git4idea.changes.GitChangeUtils
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils.addTextConvParameters
import java.nio.charset.StandardCharsets


private val LOG = logger<GitRevisionContentPreLoader>()

class GitRevisionContentPreLoader(val project: Project) {

  private val RECORD_SEPARATOR = (1..10).map { "\u0001\u0002\u0003".random() }.joinToString("")

  fun preload(root: VirtualFile, changes: Collection<Change>) {
    val toPreload = mutableMapOf<FilePath, Change>()
    val head = GitChangeUtils.resolveReference(project, root, "HEAD")
    for (change in changes) {
      val beforeRevision = change.beforeRevision
      if (beforeRevision !is GitContentRevision || beforeRevision.getRevisionNumber() != head) {
        LOG.info("Skipping change $change because beforeRevision is '${beforeRevision?.revisionNumber?.toString()}'")
        continue
      }

      val path = beforeRevision.getFile()
      toPreload[path] = change
    }
    if (toPreload.isEmpty()) {
      return
    }

    val hashesAndPaths = calcBlobHashesWithPaths(root, toPreload) ?: return

    val h = GitBinaryHandler(project, root, GitCommand.CAT_FILE)
    h.setSilent(true)
    addTextConvParameters(project, h, false)
    h.addParameters("--batch=$RECORD_SEPARATOR%(objectname)")
    h.endOptions()
    h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(
      // we need to pass '<hash> <path>', otherwise --filters parameter doesn't work
      hashesAndPaths.map { "${it.hash} ${it.relativePath}" },
      StandardCharsets.UTF_8))

    val output: ByteArray
    try {
      output = h.run()
    }
    catch (e: Exception) {
      LOG.error("Couldn't get git cat-file for $hashesAndPaths", e)
      return
    }

    val split = splitOutput(output, hashesAndPaths) ?: return

    toPreload.forEach { (path, change) ->
      val oldBeforeRevision = change.beforeRevision as GitContentRevision
      val content = split[path]
      val cache = ProjectLevelVcsManager.getInstance(project).contentRevisionCache
      cache.putIntoConstantCache(oldBeforeRevision.file, oldBeforeRevision.revisionNumber, GitVcs.getKey(), content)
    }
  }

  private fun calcBlobHashesWithPaths(root: VirtualFile, toPreload: Map<FilePath, Change>): List<HashAndPath>? {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root)!!
    val trees: List<GitIndexUtil.StagedFileOrDirectory>
    trees = GitIndexUtil.listTree(repository, toPreload.keys, HEAD)
    if (trees.size != toPreload.size) {
      LOG.warn("Incorrect number of trees ${trees.size} != ${toPreload.size}")
      return emptyList()
    }

    return trees.map { tree ->
      if (tree !is GitIndexUtil.StagedFile) {
        LOG.warn("Unexpected tree: $tree")
        return null
      }

      val relativePath = VcsFileUtil.relativePath(root, tree.path)
      if (relativePath == null) {
        LOG.error("Unexpected ls-tree output", Attachment("trees.txt", trees.joinToString()))
        return null
      }

      HashAndPath(tree.blobHash, tree.path, relativePath)
    }
  }

  private fun splitOutput(output: ByteArray, hashes: List<HashAndPath>): Map<FilePath, ByteArray>? {
    val result = mutableMapOf<FilePath, ByteArray>()
    var currentPosition = 0
    for ((hash, path, _) in hashes) {
      val separatorBytes = "$RECORD_SEPARATOR${hash}\n".toByteArray()
      if (!ArrayUtil.startsWith(output, currentPosition, separatorBytes)) {
        LOG.error("Unexpected output for hash $hash at position $currentPosition", Attachment("catfile.txt", String(output)))
        return null
      }

      val startIndex = currentPosition + separatorBytes.size

      val plainSeparatorBytes = RECORD_SEPARATOR.toByteArray()
      val nextSeparator = ArrayUtil.indexOf(output, plainSeparatorBytes, startIndex)

      val endIndex = if (nextSeparator > 0) nextSeparator else output.size
      if (endIndex > output.size) {
        LOG.error("Unexpected output for hash $hash at position $currentPosition", Attachment("catfile.txt", String(output)))
        return null
      }
      if (endIndex <= startIndex || output[endIndex - 1] != '\n'.toByte()) {
        LOG.error("Unexpected output for hash $hash at position $endIndex", Attachment("catfile.txt", String(output)))
        return null
      }

      val content = output.copyOfRange(startIndex, endIndex - 1) // -1 because the content is followed by a newline
      result[path] = content
      currentPosition = endIndex
    }

    if (result.size != hashes.size) {
      LOG.error("Invalid git cat-file output for $hashes", Attachment("catfile.txt", String(output)))
      return null
    }
    return result
  }

  private data class HashAndPath(val hash: String, val path: FilePath, val relativePath: String)

}