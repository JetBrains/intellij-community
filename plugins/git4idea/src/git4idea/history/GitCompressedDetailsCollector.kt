// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.VcsLogIndexer
import git4idea.log.GitCompressedDetails
import gnu.trove.TIntHashSet
import gnu.trove.TIntIntHashMap
import gnu.trove.TIntObjectHashMap

internal class GitCompressedDetailsCollector(project: Project, root: VirtualFile, pathsEncoder: VcsLogIndexer.PathsEncoder) :
  GitDetailsCollector<GitCompressedRecord, GitCompressedDetails>(project, root, CompressedRecordBuilder(root, pathsEncoder)) {

  override fun createCommit(records: List<GitCompressedRecord>,
                            factory: VcsLogObjectsFactory,
                            renameLimit: GitCommitRequirements.DiffRenameLimit): GitCompressedDetails {
    val metadata = GitLogUtil.createMetadata(root, records.first(), factory)
    return GitCompressedDetails(metadata, records.map { it.changes }, records.map { it.renames })
  }

  override fun createRecordsCollector(consumer: (List<GitCompressedRecord>) -> Unit): GitLogRecordCollector<GitCompressedRecord> {
    return GitLogUnorderedRecordCollector(project, root, consumer)
  }
}

internal class CompressedRecordBuilder(root: VirtualFile,
                                       private val pathsEncoder: VcsLogIndexer.PathsEncoder) : GitLogRecordBuilder<GitCompressedRecord> {
  private val rootPath = root.path
  private var changes = TIntObjectHashMap<Change.Type>()
  private var parents = TIntHashSet()
  private var renames = TIntIntHashMap()

  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    if (secondPath != null) {
      val beforeAbsolutePath = absolutePath(firstPath)
      val afterAbsolutePath = absolutePath(secondPath)
      val beforeId = pathsEncoder.encode(beforeAbsolutePath, false)
      val afterId = pathsEncoder.encode(afterAbsolutePath, false)
      addPath(beforeAbsolutePath, beforeId, Change.Type.DELETED)
      addPath(afterAbsolutePath, afterId, Change.Type.NEW)
      renames.put(beforeId, afterId)
    }
    else {
      val absolutePath = absolutePath(firstPath)
      val pathId = pathsEncoder.encode(absolutePath, false)
      addPath(absolutePath, pathId, type)
    }
  }

  private fun addPath(absolutePath: String, pathId: Int, type: Change.Type) {
    changes.put(pathId, type)
    addParents(absolutePath)
  }

  private fun addParents(path: String) {
    var parentPath = PathUtil.getParentPath(path)
    var parentPathId = pathsEncoder.encode(parentPath, true)

    while (!parents.contains(parentPathId)) {
      if (FileUtil.PATH_HASHING_STRATEGY.equals(rootPath, parentPath)) break

      parents.add(parentPathId)

      parentPath = PathUtil.getParentPath(parentPath)
      parentPathId = pathsEncoder.encode(parentPath, true)
    }
  }

  private fun absolutePath(path: String) = "$rootPath/$path"

  override fun build(options: MutableMap<GitLogParser.GitLogOption, String>, supportsRawBody: Boolean): GitCompressedRecord {
    parents.forEach {
      changes.put(it, Change.Type.MODIFICATION)
      true
    }
    return GitCompressedRecord(options, changes, renames, supportsRawBody)
  }

  override fun clear() {
    changes = TIntObjectHashMap()
    parents = TIntHashSet()
    renames = TIntIntHashMap()
  }
}

internal class GitCompressedRecord(options: MutableMap<GitLogParser.GitLogOption, String>,
                                   val changes: TIntObjectHashMap<Change.Type>,
                                   val renames: TIntIntHashMap,
                                   supportsRawBody: Boolean) : GitLogRecord(options, supportsRawBody)