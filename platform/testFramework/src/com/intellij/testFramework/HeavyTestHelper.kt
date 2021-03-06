// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
internal object HeavyTestHelper {
  @JvmStatic
  fun createTestProjectStructure(module: Module?,
                                 rootPath: String?,
                                 dir: Path,
                                 addProjectRoots: Boolean): VirtualFile {
    val virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
                     ?: throw IllegalStateException("Cannot find virtual directory by $dir")
    virtualDir.getChildren()
    virtualDir.refresh(false, true)
    WriteAction.computeAndWait<Unit, IOException> {
      if (rootPath != null) {
        val vDir1 = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'))
                    ?: throw Exception("$rootPath not found")
        VfsUtil.copyDirectory(null, vDir1, virtualDir, null)
      }
      if (addProjectRoots) {
        ModuleRootModificationUtil.modifyModel(module!!) { model ->
          model.addContentEntry(virtualDir).addSourceFolder(virtualDir, false)
          true
        }
      }
    }
    return virtualDir
  }

  @JvmStatic
  fun createTempDirectoryForTempDirTestFixture(dir: Path?, prefix: String): Path {
    val parentDir = dir ?: Paths.get(FileUtil.getTempDirectory())
    Files.createDirectories(parentDir)
    return Files.createTempDirectory(parentDir, prefix)
  }

  @JvmStatic
  fun getOrCreateProjectBaseDir(project: Project): VirtualFile {
    val basePath = project.stateStore.getProjectBasePath()
    val fs = LocalFileSystem.getInstance()
    val baseDir = fs.findFileByNioFile(basePath)
    if (baseDir == null) {
      Files.createDirectories(basePath)
      return fs.refreshAndFindFileByNioFile(basePath)!!
    }
    return baseDir
  }
}