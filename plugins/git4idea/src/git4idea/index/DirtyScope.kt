// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.impl.projectlevelman.FilePathMapping
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import git4idea.util.toShortenedString
import gnu.trove.THashSet
import java.util.*

internal sealed class DirtyScope(val root: VirtualFile) {
  abstract fun belongsTo(root: VirtualFile, file: FilePath): Boolean
  abstract fun dirtyPaths(): List<FilePath>
  abstract fun addDirtyPaths(paths: Collection<FilePath>, recursive: Boolean)
  open fun pack() = Unit

  internal class Root(root: VirtualFile) : DirtyScope(root) {
    override fun belongsTo(root: VirtualFile, file: FilePath) = this.root == root
    override fun dirtyPaths(): List<FilePath> = emptyList()
    override fun addDirtyPaths(paths: Collection<FilePath>, recursive: Boolean) = Unit
    override fun toString(): String {
      return "DirtyScope.Root(root=${root.name})"
    }
  }

  internal class Paths(root: VirtualFile) : DirtyScope(root) {
    private val hashingStrategy = ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY
    private var dirtyFiles: THashSet<FilePath> = THashSet(hashingStrategy)
    private var dirtyDirs = RecursiveFilePathSet()

    override fun belongsTo(root: VirtualFile, file: FilePath): Boolean {
      if (root != this.root) return false
      return root == file.virtualFile || dirtyFiles.contains(file) || dirtyDirs.hasAncestor(file)
    }

    override fun dirtyPaths(): List<FilePath> = dirtyDirs.filePaths().union(dirtyFiles).toList()
    override fun addDirtyPaths(paths: Collection<FilePath>, recursive: Boolean) {
      for (path in paths) {
        if (dirtyDirs.hasAncestor(path)) continue

        if (recursive) dirtyDirs.add(path)
        else dirtyFiles.add(path)
      }
    }

    override fun pack() {
      dirtyDirs = dirtyDirs.packed()
      dirtyFiles = THashSet(dirtyFiles.filterNot { dirtyDirs.hasAncestor(it) }, hashingStrategy)
    }

    fun addTo(other: DirtyScope) {
      other.addDirtyPaths(dirtyDirs.filePaths(), true)
      other.addDirtyPaths(dirtyFiles, false)
      other.pack()
    }

    override fun toString(): String {
      return "DirtyScope.Paths(root=${root.name}, files=${dirtyFiles.toShortenedString(separator = ",\n")},\n" +
             "dirs=${dirtyDirs.filePaths().toShortenedString(separator = ",\n")})"
    }

    private class RecursiveFilePathSet {
      private val filePathMapping: FilePathMapping<FilePath> = FilePathMapping(true)
      fun add(filePath: FilePath) = filePathMapping.add(filePath.path, filePath)
      fun hasAncestor(filePath: FilePath): Boolean = filePathMapping.getMappingFor(filePath) != null
      fun filePaths(): Collection<FilePath> = filePathMapping.values()

      fun packed(): RecursiveFilePathSet {
        val result = RecursiveFilePathSet()
        val paths = ContainerUtil.sorted(filePaths(), Comparator.comparingInt { it.path.length })
        for (path in paths) {
          if (result.hasAncestor(path)) continue
          result.add(path)
        }
        return result
      }
    }
  }
}