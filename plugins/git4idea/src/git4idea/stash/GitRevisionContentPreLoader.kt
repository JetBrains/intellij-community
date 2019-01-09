// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.getRelativePath
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.CharsetToolkit.UTF8_CHARSET
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.GitRevisionNumber.HEAD
import git4idea.changes.GitChangeUtils
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils.addTextConvParameters
import java.nio.charset.Charset
import java.util.*


private val LOG = logger<GitRevisionContentPreLoader>()
private const val SEPARATOR = "\u0001\u0002\u0003"

class GitRevisionContentPreLoader(val project: Project) {

  fun preload(root: VirtualFile, changes: Collection<Change>): Collection<Change> {
    val skippedChanges = HashSet<Change>()
    val toPreload = mutableListOf<Info>()
    val head = GitChangeUtils.resolveReference(project, root, "HEAD")
    for (change in changes) {
      val beforeRevision = change.beforeRevision
      if (beforeRevision !is GitContentRevision || beforeRevision.getRevisionNumber() != head) {
        skippedChanges.add(change)
      }
      else {
        toPreload.add(Info(beforeRevision.getFile(), change))
      }
    }
    if (toPreload.isEmpty()) {
      return changes
    }

    val hashes = calcBlobHashesWithPaths(root, toPreload, skippedChanges)
    if (hashes.isEmpty()) {
      return changes
    }

    val h = GitBinaryHandler(project, root, GitCommand.CAT_FILE)
    h.setSilent(true)
    addTextConvParameters(project, h, false)
    h.addParameters("--batch=$SEPARATOR")
    h.endOptions()
    h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(hashes, UTF8_CHARSET))

    val output: ByteArray
    try {
      output = h.run() 
    }
    catch (e: Exception) {
      LOG.error("Couldn't get git cat-file for $hashes", e)
      return changes
    }

    val split = splitOutput(output)
    if (split.size != toPreload.size) {
      LOG.error("Invalid git cat-file output for $hashes", Attachment("catfile.txt", String(output)))
      return changes
    }

    toPreload.forEachIndexed { index, info -> info.content = split[index] }

    val preloadedChanges = toPreload.map { info ->
      val oldBeforeRevision = info.change.beforeRevision as GitContentRevision
      val beforeRevision = PreloadedGitContentRevision(project, oldBeforeRevision.file,
                                                       oldBeforeRevision.revisionNumber as GitRevisionNumber,
                                                       oldBeforeRevision.charset,
                                                       info.content)
      val afterRevision = info.change.afterRevision
      Change(beforeRevision, afterRevision)
    }

    return skippedChanges + preloadedChanges
  }

  private fun calcBlobHashesWithPaths(root: VirtualFile,
                                      toPreload: MutableList<Info>,
                                      skippedChanges: HashSet<Change>): List<String> {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root)!!
    val trees: List<GitIndexUtil.StagedFileOrDirectory>
    trees = GitIndexUtil.listTree(repository, toPreload.map { it.filePath }, HEAD)
    val filteredTrees: List<GitIndexUtil.StagedFile> = trees.mapIndexedNotNull { index: Int, tree: GitIndexUtil.StagedFileOrDirectory ->
      if (tree is GitIndexUtil.StagedFile) tree
      else {
        skippedChanges.add(toPreload[index].change)
        null
      }
    }
    return filteredTrees.map { stagedFile ->
      // we need to pass the path to make --filters parameter work
      val relativePath = getRelativePath(virtualToIoFile(root), stagedFile.path.ioFile)
      "${stagedFile.blobHash} ${relativePath}"
    }
  }

  private fun splitOutput(output: ByteArray): MutableList<ByteArray> {
    val separatorBytes = "\n$SEPARATOR\n".toByteArray()
    val prefix = "$SEPARATOR\n".toByteArray()
    val outputForSplit = output.copyOfRange(prefix.size,
                                            output.size - 1) // split out initial $separator\n and the trailing \n at the very end
    return ArrayUtil.splitBytes(outputForSplit, separatorBytes)
  }

  private data class Info(val filePath: FilePath, val change: Change) {
    lateinit var content: ByteArray
  }

  private class PreloadedGitContentRevision(project: Project,
                                            file: FilePath,
                                            revision: GitRevisionNumber,
                                            charset: Charset?,
                                            val content: ByteArray) : GitContentRevision(file, revision, project, charset) {
    override fun getContentAsBytes(): ByteArray {
      return content
    }
  }
}