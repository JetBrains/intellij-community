// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsLogManager
import git4idea.GitVcs

fun getGitRootFiles(project: Project, files: Collection<FilePath>): Map<VirtualFile, Collection<FilePath>> {
  val rootFiles = HashMap<VirtualFile, HashSet<FilePath>>()
  val projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project)
  files.forEach { filePath ->
    val fileVcs = projectLevelVcsManager.getVcsRootObjectFor(filePath)
    if (fileVcs != null && fileVcs.vcs is GitVcs) {
      rootFiles.getOrPut(fileVcs.path) { HashSet() }.add(filePath)
    }
  }
  return rootFiles
}

fun <T, K> MutableMap<T, K>.retainAll(keys: Collection<T>) =
  this.keys.subtract(keys).forEach {
    this.remove(it)
  }

internal fun Project.getGitRoots() = ProjectLevelVcsManager.getInstance(this).allVcsRoots.filter { it.vcs is GitVcs }

internal fun Project.anyGitRootsForIndexing(): Boolean {
  val gitRoots = this.getGitRoots()
  val rootsForIndex = VcsLogPersistentIndex.getRootsForIndexing(VcsLogManager.findLogProviders(gitRoots, this))

  return rootsForIndex.isNotEmpty()
}