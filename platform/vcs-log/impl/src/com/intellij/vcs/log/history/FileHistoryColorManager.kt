// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.history.FileHistoryPaths.filePaths
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import com.intellij.vcsUtil.VcsFileUtil
import java.awt.Color

internal class FileHistoryColorManager(private val root: VirtualFile, private val path: FilePath) : VcsLogColorManager {
  private var baseColorManager: VcsLogColorManager = VcsLogColorManagerFactory.create(setOf(path))

  override fun getPathColor(path: FilePath, colorSpace: String): Color {
    return baseColorManager.getPathColor(path, colorSpace)
  }

  fun update(pack: VcsLogDataPack) {
    val pathsFromPack = pack.filePaths()
    if (pathsFromPack.isEmpty()) {
      baseColorManager = VcsLogColorManagerFactory.create(setOf(path))
    }
    else {
      val newPaths = CollectionFactory.createLinkedCustomHashingStrategySet(FILE_PATH_HASHING_STRATEGY).also {
        it.addAll(baseColorManager.paths)
      }
      newPaths.retainAll(pathsFromPack)
      newPaths.addAll(pathsFromPack)
      baseColorManager = VcsLogColorManagerFactory.create(newPaths)
    }
  }

  override fun getPaths(): Collection<FilePath> {
    return baseColorManager.paths
  }

  override fun getLongName(path: FilePath): String {
    return VcsFileUtil.relativePath(root, path)
  }
}
