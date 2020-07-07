// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.rd.attach
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class JpsProjectExternalStorageTest {
  @Rule
  @JvmField
  var application = ApplicationRule()

  @JvmField
  @Rule
  val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  @Test
  fun loadUnloadedModules() = loadProjectAndCheckResults("unloadedModules") { project ->
    val unloadedModuleName = "imported"
    val moduleManager = ModuleManagerComponentBridge.getInstance(project)
    val moduleDescription = moduleManager.getUnloadedModuleDescription(unloadedModuleName)
    assertNotNull(moduleDescription)
    val contentRoots = moduleDescription!!.contentRoots
    assertEquals(1, contentRoots.size)
    assertEquals(unloadedModuleName, contentRoots[0].fileName)

    val unloadedModules = moduleManager.unloadedModules
    assertEquals(1, unloadedModules.size)
    assertTrue(unloadedModules.containsKey(unloadedModuleName))
  }

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/workspaceModel/ide/tests/testData/serialization")

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: (Project) -> Unit) {
    fun copyProjectFiles(dir: VirtualFile): Path {
      val projectDir = VfsUtil.virtualToIoFile(dir)
      val testProjectFilesDir = testDataRoot.resolve(testDataDirName).resolve("project").toFile()
      if (testProjectFilesDir.exists()) {
        FileUtil.copyDir(testProjectFilesDir, projectDir)
      }
      val testCacheFilesDir = testDataRoot.resolve(testDataDirName).resolve("cache").toFile()
      if (testCacheFilesDir.exists()) {
        val cachePath = appSystemDir.resolve("external_build_system").resolve(getProjectCacheFileName(projectDir.absolutePath))
        FileUtil.copyDir(testCacheFilesDir, cachePath.toFile())
      }
      VfsUtil.markDirtyAndRefresh(false, true, true, dir)
      return projectDir.toPath()
    }

    runInEdtAndWait {
      val project = loadProject(disposableRule) { copyProjectFiles(it) }
      project.runInLoadComponentStateMode { checkProject(project) }
    }
  }

  private fun loadProject(disposableRule: DisposableRule, projectCreator: (VirtualFile) -> Path): Project {
    val projectDir = projectCreator(tempDirManager.newVirtualDirectory())
    val project = WorkspaceModelInitialTestContent.withInitialContent(WorkspaceEntityStorageBuilder.create()) {
      PlatformTestUtil.loadAndOpenProject(projectDir)
    }
    disposableRule.disposable.attach { PlatformTestUtil.forceCloseProjectWithoutSaving(project) }
    return project
  }
}