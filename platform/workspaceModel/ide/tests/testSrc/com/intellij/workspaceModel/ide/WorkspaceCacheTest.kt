// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.LoadedProjectData
import com.intellij.workspaceModel.ide.impl.jps.serialization.copyAndLoadProject
import com.intellij.workspaceModel.storage.EntityStorageSerializer
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.apache.commons.lang.RandomStringUtils
import org.junit.*
import org.junit.Assert.assertEquals
import java.io.File

class WorkspaceCacheTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule(true)

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  private lateinit var serializer: EntityStorageSerializer

  @Before
  fun setUp() {
    WorkspaceModelImpl.forceEnableCaching = true
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
    serializer = EntityStorageSerializerImpl(WorkspaceModelCacheImpl.PluginAwareEntityTypesResolver, virtualFileManager)
  }

  @After
  fun tearDown() {
    WorkspaceModelImpl.forceEnableCaching = false
    WorkspaceModelCacheImpl.testCacheFile = null
  }

  @Test
  fun `save non-persistent modules`() {
    val projectData = prepareProject()

    val project = loadProject(projectData.projectDir)

    val initialModulesSize = ModuleManager.getInstance(project).modules.size
    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        ModuleManager.getInstance(project).newNonPersistentModule("Non", "Non")
      }
    }

    WorkspaceModel.getInstance(project).cache?.saveCacheNow()

    val project2 = loadProject(projectData.projectDir)

    val modules = ModuleManager.getInstance(project2).modules
    assertEquals(initialModulesSize, modules.size)
  }

  private fun prepareProject(): LoadedProjectData {
    Assume.assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val projectFile = projectFile("moduleAdded/after")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)
    val storage = projectData.storage

    val cacheFile = projectData.projectDir.resolve(cacheFileName())
    cacheFile.createNewFile()
    WorkspaceModelCacheImpl.testCacheFile = cacheFile

    cacheFile.outputStream().use {
      serializer.serializeCache(it, storage)
    }
    return projectData
  }

  private fun loadProject(projectDir: File): Project {
    val project = PlatformTestUtil.loadAndOpenProject(projectDir.toPath(), disposableRule.disposable)
    Disposer.register(disposableRule.disposable, Disposable {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    })
    return project
  }


  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    private val testProjectBase = "platform/workspaceModel/ide/tests/testData/serialization/loadingFromCache"
    private val dirBasedProject = "$testProjectBase/directoryBased"

    private fun projectFile(path: String): File {
      return File(PathManagerEx.getCommunityHomePath(), "$dirBasedProject/$path")
    }

    private fun cacheFileName(): String {
      return "test_caching_" + RandomStringUtils.randomAlphabetic(5) + ".data"
    }
  }
}