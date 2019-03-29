// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.VcsLogIndexer
import git4idea.log.GitCompressedDetails
import gnu.trove.TIntHashSet
import gnu.trove.TIntIntHashMap
import gnu.trove.TIntObjectHashMap
import kotlin.math.max

internal class GitCompressedDetailsCollector(project: Project, root: VirtualFile, pathsEncoder: VcsLogIndexer.PathsEncoder) :
  GitDetailsCollector<GitCompressedRecord, GitCompressedDetails>(project, root, CompressedRecordBuilder(root, pathsEncoder)) {
  
  override fun createCommit(records: List<GitCompressedRecord>,
                            factory: VcsLogObjectsFactory,
                            renameLimit: GitCommitRequirements.DiffRenameLimit): GitCompressedDetails {
    val hasRenames = when (renameLimit) {
      GitCommitRequirements.DiffRenameLimit.INFINITY -> true
      GitCommitRequirements.DiffRenameLimit.GIT_CONFIG -> false // need to know the value from git.config to give correct answer
      GitCommitRequirements.DiffRenameLimit.NO_RENAMES -> false
      GitCommitRequirements.DiffRenameLimit.REGISTRY -> {
        val renameLimitEstimate = records.map { it.renameLimitEstimate }.max() ?: 0
        renameLimitEstimate <= Registry.intValue("git.diff.renameLimit")
      }
    }
    val metadata = GitLogUtil.createMetadata(root, records.first(), factory)
    return GitCompressedDetails(metadata, records.map { it.changes }, records.map { it.renames }, hasRenames)
  }

  override fun createRecordsCollector(preserveOrder: Boolean,
                                      consumer: (List<GitCompressedRecord>) -> Unit): GitLogRecordCollector<GitCompressedRecord> {
    return if (preserveOrder)
      object : GitLogRecordCollector<GitCompressedRecord>(project, root, consumer) {
        override fun createEmptyCopy(r: GitCompressedRecord): GitCompressedRecord =
          GitCompressedRecord(r.options, TIntObjectHashMap(), TIntIntHashMap(), 0, r.isSupportsRawBody)
      }
    else
      object : GitLogUnorderedRecordCollector<GitCompressedRecord>(project, root, consumer) {
        override fun getSize(r: GitCompressedRecord) = r.changes.size() + r.renames.size()
        override fun createEmptyCopy(r: GitCompressedRecord): GitCompressedRecord =
          GitCompressedRecord(r.options, TIntObjectHashMap(), TIntIntHashMap(), 0, r.isSupportsRawBody)
      }
  }

}

internal class CompressedRecordBuilder(private val root: VirtualFile,
                                       private val pathsEncoder: VcsLogIndexer.PathsEncoder) : GitLogRecordBuilder<GitCompressedRecord> {
  private var changes = TIntObjectHashMap<Change.Type>()
  private var parents = TIntHashSet()
  private var renames = TIntIntHashMap()
  private var sourcesCount = 0
  private var targetsCount = 0


  override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
    if (secondPath != null) {
      changes.put(pathsEncoder.encode(absolutePath(firstPath), false), Change.Type.DELETED)
      changes.put(pathsEncoder.encode(absolutePath(secondPath), false), Change.Type.NEW)
      addParents(firstPath)
      addParents(secondPath)
    }
    else {
      changes.put(pathsEncoder.encode(absolutePath(firstPath), false), type)
      addParents(firstPath)
    }
    when (type) {
      Change.Type.NEW -> targetsCount++
      Change.Type.DELETED -> sourcesCount++
      else -> {
      }
    }
  }

  private fun addParents(path: String) {
    var parentPath = PathUtil.getParentPath(path)
    var parentPathId = pathsEncoder.encode(parentPath, true)

    while (!parents.contains(parentPathId)) {
      if (FileUtil.PATH_HASHING_STRATEGY.equals(root.path, parentPath)) break

      parents.add(parentPathId)

      parentPath = PathUtil.getParentPath(parentPath)
      parentPathId = pathsEncoder.encode(parentPath, true)
    }
  }

  private fun absolutePath(firstPath: String) = root.path + "/" + firstPath

  override fun build(options: MutableMap<GitLogParser.GitLogOption, String>, supportsRawBody: Boolean): GitCompressedRecord {
    parents.forEach {
      changes.put(it, Change.Type.MODIFICATION)
      true
    }
    return GitCompressedRecord(options, changes, renames, max(sourcesCount, targetsCount), supportsRawBody)
  }

  override fun clear() {
    changes = TIntObjectHashMap()
    parents = TIntHashSet()
    renames = TIntIntHashMap()
    sourcesCount = 0
    targetsCount = 0
  }
}

internal class GitCompressedRecord(options: MutableMap<GitLogParser.GitLogOption, String>,
                                   val changes: TIntObjectHashMap<Change.Type>,
                                   val renames: TIntIntHashMap,
                                   val renameLimitEstimate: Int,
                                   supportsRawBody: Boolean) : GitLogRecord(options, supportsRawBody)