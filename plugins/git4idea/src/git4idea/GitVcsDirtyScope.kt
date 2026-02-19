// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.VcsDirtyScopeBuilder
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope
import com.intellij.openapi.vcs.util.paths.RootDirtySet
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.MultiMap
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

class GitVcsDirtyScope(private val project: Project) : VcsModifiableDirtyScope(), VcsDirtyScopeBuilder {
  private val vcs = GitVcs.getInstance(project)

  private val dirtyRootsUnder = MultiMap<FilePath, VirtualFile>()

  private val dirtyDirectories: MutableMap<VirtualFile, RootDirtySet> = HashMap()
  private var wasEverythingDirty = false

  init {
    for (root in ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)) {
      val parentFile = root.getParent() ?: continue

      var parentPath: FilePath? = VcsUtil.getFilePath(parentFile)
      while (parentPath != null) {
        dirtyRootsUnder.putValue(parentPath, root)
        parentPath = parentPath.getParentPath()
      }
    }
  }

  override fun getProject(): Project = project
  override fun getVcs(): AbstractVcs = vcs

  fun getDirtySetsPerRoot(): Map<VirtualFile, RootDirtySet> {
    return dirtyDirectories.mapValues { it.value.copy() }
  }

  override fun getAffectedContentRoots(): Collection<VirtualFile> = dirtyDirectories.keys
  override fun getDirtyFiles(): Set<FilePath> = emptySet()
  override fun getDirtyFilesNoExpand(): Set<FilePath> = emptySet()

  override fun getRecursivelyDirtyDirectories(): Set<FilePath> {
    val result = CollectionFactory.createCustomHashingStrategySet(ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY)
    for (dirtySet in dirtyDirectories.values) {
      result.addAll(dirtySet.collectFilePaths())
    }
    return result
  }

  override fun markRootDirty(vcsRoot: VirtualFile) {
    addDirtyPathFast(vcsRoot, VcsUtil.getFilePath(vcsRoot), false)
  }

  override fun addDirtyPathFast(vcsRoot: VirtualFile, filePath: FilePath, recursively: Boolean) {
    val rootSet = dirtyDirectories.computeIfAbsent(vcsRoot, ::createDirtySetForRoot)
    rootSet.markDirty(filePath)

    if (recursively) {
      for (root in dirtyRootsUnder[filePath]) {
        val subRootSet = dirtyDirectories.computeIfAbsent(root, ::createDirtySetForRoot)
        subRootSet.markEverythingDirty()
      }
    }
  }

  override fun markEverythingDirty() {
    wasEverythingDirty = true
  }

  override fun pack(): GitVcsDirtyScope {
    val copy = GitVcsDirtyScope(project)
    copy.wasEverythingDirty = wasEverythingDirty

    for ((root, dirtySet) in dirtyDirectories) {
      copy.dirtyDirectories[root] = dirtySet.compact()
    }
    return copy
  }

  override fun addDirtyDirRecursively(newcomer: FilePath) {
    val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(newcomer)
    if (vcsRoot == null || vcsRoot.vcs !== vcs) return
    addDirtyPathFast(vcsRoot.path, newcomer, true)
  }

  override fun addDirtyFile(newcomer: FilePath) {
    val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(newcomer)
    if (vcsRoot == null || vcsRoot.vcs !== vcs) return
    addDirtyPathFast(vcsRoot.path, newcomer, false)
  }

  override fun wasEveryThingDirty(): Boolean = wasEverythingDirty
  override fun isEmpty(): Boolean = dirtyDirectories.isEmpty()

  override fun belongsTo(path: FilePath): Boolean {
    val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(path)
    if (vcsRoot == null || vcsRoot.vcs !== vcs) {
      return false
    }

    val rootSet = dirtyDirectories[vcsRoot.path]
    return rootSet != null && rootSet.belongsTo(path)
  }

  override fun toString(): @NonNls String {
    val result: @NonNls StringBuilder = StringBuilder("GitVcsDirtyScope[")
    for ((root, dirtyFiles) in dirtyDirectories.entries) {
      result.append("Root: $root -> ").append("{${dirtyFiles.collectFilePaths()}}")
    }
    result.append("]")
    return result.toString()
  }

  companion object {
    @ApiStatus.Internal
    @JvmStatic
    fun createDirtySetForRoot(vcsRoot: VirtualFile): RootDirtySet {
      return RootDirtySet(VcsUtil.getFilePath(vcsRoot), true)
    }
  }
}
