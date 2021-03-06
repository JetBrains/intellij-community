// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.vcsUtil.VcsUtil

internal class DirtBuilder {
  private val scopesByVcs: MutableMap<AbstractVcs, VcsDirtyScopeImpl> = mutableMapOf()
  var isEverythingDirty: Boolean = false
    private set

  fun markEverythingDirty() {
    isEverythingDirty = true
    scopesByVcs.clear()
  }

  fun addDirtyFiles(vcsRoot: VcsRoot, files: Collection<FilePath>, dirs: Collection<FilePath>): Boolean {
    if (isEverythingDirty) return true

    val vcs = vcsRoot.vcs
    val root = vcsRoot.path
    if (vcs != null) {
      val scope = scopesByVcs.computeIfAbsent(vcs) { VcsDirtyScopeImpl(it, isEverythingDirty) }
      for (filePath in files) {
        scope.addDirtyPathFast(root, filePath, false)
      }
      for (filePath in dirs) {
        scope.addDirtyPathFast(root, filePath, true)
      }
    }

    return scopesByVcs.isNotEmpty()
  }

  fun buildScopes(project: Project): List<VcsDirtyScopeImpl> {
    val scopes: Collection<VcsDirtyScopeImpl>
    if (isEverythingDirty) {
      val allScopes = mutableMapOf<AbstractVcs, VcsDirtyScopeImpl>()
      for (root in ProjectLevelVcsManager.getInstance(project).allVcsRoots) {
        val vcs = root.vcs
        val path = root.path
        if (vcs != null) {
          val scope = allScopes.computeIfAbsent(vcs) { VcsDirtyScopeImpl(it, isEverythingDirty) }
          scope.addDirtyPathFast(path, VcsUtil.getFilePath(path), true)
        }
      }
      scopes = allScopes.values
    }
    else {
      scopes = scopesByVcs.values
    }
    return scopes.map { it.pack() }
  }

  fun isFileDirty(filePath: FilePath): Boolean = isEverythingDirty || scopesByVcs.values.any { it.belongsTo(filePath) }
}