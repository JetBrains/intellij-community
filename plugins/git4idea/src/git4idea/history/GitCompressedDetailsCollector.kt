// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.VcsLogIndexer
import git4idea.log.GitCompressedDetails
import it.unimi.dsi.fastutil.ints.*

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

internal class CompressedRecordBuilder(private val root: VirtualFile,
                                       private val pathsEncoder: VcsLogIndexer.PathsEncoder) : GitLogRecordBuilder<GitCompressedRecord> {
  private var changes = Int2ObjectOpenHashMap<Change.Type>()
  private var parents = IntOpenHashSet()
  private var renames = Int2IntOpenHashMap()

  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    if (secondPath != null) {
      val beforeId = pathsEncoder.encode(root, firstPath, false)
      val afterId = pathsEncoder.encode(root, secondPath, false)
      addPath(firstPath, beforeId, Change.Type.DELETED)
      addPath(secondPath, afterId, Change.Type.NEW)
      renames.put(beforeId, afterId)
    }
    else {
      val pathId = pathsEncoder.encode(root, firstPath, false)
      addPath(firstPath, pathId, type)
    }
  }

  private fun addPath(relativePath: String, pathId: Int, type: Change.Type) {
    changes.put(pathId, type)
    addParents(relativePath)
  }

  private fun addParents(path: String) {
    var parentPath = PathUtil.getParentPath(path)
    var parentPathId = pathsEncoder.encode(root, parentPath, true)

    while (!parents.contains(parentPathId)) {
      if (parentPath.isEmpty()) break

      parents.add(parentPathId)

      parentPath = PathUtil.getParentPath(parentPath)
      parentPathId = pathsEncoder.encode(root, parentPath, true)
    }
  }

  override fun build(options: MutableMap<GitLogParser.GitLogOption, String>, supportsRawBody: Boolean): GitCompressedRecord {
    val iterator = parents.iterator()
    while (iterator.hasNext()) {
      changes.put(iterator.nextInt(), Change.Type.MODIFICATION)
    }
    return GitCompressedRecord(options, changes, renames, supportsRawBody)
  }

  override fun clear() {
    changes = Int2ObjectOpenHashMap()
    parents = IntOpenHashSet()
    renames = Int2IntOpenHashMap()
  }
}

internal class GitCompressedRecord(options: MutableMap<GitLogParser.GitLogOption, String>,
                                   val changes: Int2ObjectMap<Change.Type>,
                                   val renames: Int2IntMap,
                                   supportsRawBody: Boolean) : GitLogRecord(options, supportsRawBody)