// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.configurationStore

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.CommonProcessors
import java.nio.file.Path
import java.nio.file.Paths

suspend fun copyFilesAndReloadProject(project: Project, fromDir: Path) {
  val base = Paths.get(project.basePath!!)
  val projectDir = VfsUtil.findFile(base, true)!!
  //process all files to ensure that they all are loaded in VFS and we'll get events when they are changed
  VfsUtil.processFilesRecursively(projectDir, CommonProcessors.alwaysTrue())

  FileUtil.copyDir(fromDir.toFile(), base.toFile())
  VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
  StoreReloadManager.getInstance().reloadChangedStorageFiles()
}
