// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.createFile
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@ApiStatus.Internal
internal object HeavyTestHelper {
  // not in HeavyIdeaTestFixtureImpl because of java
  @JvmStatic
  fun openHeavyTestFixtureProject(path: Path, moduleListener: ModuleListener): Project {
    val options = createTestOpenProjectOptions().copy(beforeOpen = {
      it.messageBus.simpleConnect().subscribe(ProjectTopics.MODULES, moduleListener)
      true
    })
    val project = ProjectManagerEx.getInstanceEx().openProject(path, options)!!
    if (ApplicationManager.getApplication().isDispatchThread) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    return project
  }

  // not in HeavyIdeaTestFixtureImpl to avoid IOException in API
  @JvmStatic
  fun createModuleAt(moduleName: String,
                     project: Project,
                     moduleType: ModuleType<*>,
                     path: String,
                     isCreateProjectFileExplicitly: Boolean,
                     filesToDelete: MutableCollection<Path>): Module {
    if (isCreateProjectFileExplicitly) {
      val moduleFile = Paths.get(path, "$moduleName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
      try {
        moduleFile.createFile()
      }
      catch (ignore: FileAlreadyExistsException) {
      }

      filesToDelete.add(moduleFile)
      return WriteAction.computeAndWait<Module, RuntimeException> {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(moduleFile)!!
        val module = ModuleManager.getInstance(project).newModule(virtualFile.path, moduleType.id)
        module.moduleFile
        module
      }
    }
    else {
      val moduleManager = ModuleManager.getInstance(project)
      return WriteAction.computeAndWait<Module, RuntimeException> {
        moduleManager.newModule(path + File.separatorChar + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION, moduleType.id)
      }
    }
  }

  @JvmStatic
  fun createTestProjectStructure(tempName: String,
                                 module: Module?,
                                 rootPath: String?,
                                 filesToDelete: MutableCollection<Path>,
                                 addProjectRoots: Boolean): VirtualFile {
    val dir = createTempDirectoryForTempDirTestFixture(null, tempName)
    filesToDelete.add(dir)
    val vDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
    assert(vDir != null && vDir.isDirectory) { dir }
    HeavyPlatformTestCase.synchronizeTempDirVfs(vDir!!)
    WriteAction.runAndWait<RuntimeException> {
      if (rootPath != null) {
        val vDir1 = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'))
                    ?: throw Exception("$rootPath not found")
        VfsUtil.copyDirectory(null, vDir1, vDir, null)
      }
      if (addProjectRoots) {
        PsiTestUtil.addSourceContentToRoots(module!!, vDir)
      }
    }
    return vDir
  }

  @JvmStatic
  fun createTempDirectoryForTempDirTestFixture(dir: Path?, prefix: String): Path {
    val parentDir = dir ?: Paths.get(FileUtil.getTempDirectory())
    Files.createDirectories(parentDir)
    return Files.createTempDirectory(parentDir, prefix)
  }
}