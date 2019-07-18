// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import java.util.concurrent.atomic.AtomicReference

@State(name = "VcsDirectoryRenames", storages = [Storage(value = "vcs.xml")])
internal class VcsDirectoryRenamesProvider : PersistentStateComponent<Array<RenameRecord>> {
  private val renames: AtomicReference<Map<EdgeData<CommitId>, EdgeData<FilePath>>> = AtomicReference(emptyMap())

  val renamesMap: Map<EdgeData<CommitId>, EdgeData<FilePath>>
    get() = renames.get()

  override fun getState(): Array<RenameRecord>? {
    return renames.get().entries.groupBy({ it.value }) { it.key }.map { entry ->
      val paths = entry.key
      val commits = entry.value
      val root = commits.first().parent.root
      RenameRecord(root.path, VcsFileUtil.relativePath(root, paths.parent), VcsFileUtil.relativePath(root, paths.child),
                   commits.map { Hashes(it) }.toTypedArray())
    }.toTypedArray()
  }

  override fun loadState(state: Array<RenameRecord>) {
    renames.set(state.flatMap { record -> record.getCommitsAndPaths() }.toMap(linkedMapOf()))
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsDirectoryRenamesProvider = project.service()
  }
}

@Tag("hashes")
internal data class Hashes(@Attribute("parentHash") val parentHash: String,
                           @Attribute("childHash") val childHash: String) {
  internal constructor() : this("", "")

  internal constructor(edgeData: EdgeData<CommitId>) : this(edgeData.parent.hash.asString(), edgeData.child.hash.asString())

  fun getCommitIds(rootFile: VirtualFile): EdgeData<CommitId>? {
    if (!VcsLogUtil.HASH_REGEX.matcher(parentHash).matches() || !VcsLogUtil.HASH_REGEX.matcher(childHash).matches()) return null
    return EdgeData(CommitId(HashImpl.build(parentHash), rootFile), CommitId(HashImpl.build(childHash), rootFile))
  }
}

@Tag("rename")
internal class RenameRecord(@Attribute("root") val root: String,
                            @Attribute("parentPath") val parentPath: String,
                            @Attribute("childPath") val childPath: String,
                            @XCollection(propertyElementName = "hashesList") val hashes: Array<Hashes>) {
  internal constructor() : this("", "", "", emptyArray())

  private val rootFile by lazy { LocalFileSystem.getInstance().findFileByPath(root) }

  private fun getPaths(root: VirtualFile) =
    EdgeData(VcsUtil.getFilePath(root, parentPath, true), VcsUtil.getFilePath(root, childPath, true))

  private fun getCommits(root: VirtualFile) = hashes.mapNotNull { it.getCommitIds(root) }

  fun getCommitsAndPaths(): List<Pair<EdgeData<CommitId>, EdgeData<FilePath>>> {
    return rootFile?.let { root ->
      val paths = getPaths(root)
      getCommits(root).map { Pair(it, paths) }
    } ?: emptyList()
  }
}