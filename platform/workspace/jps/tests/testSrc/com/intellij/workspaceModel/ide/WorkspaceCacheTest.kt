// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.workspace.storage.EntityStorageSerializer
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer
import com.intellij.workspaceModel.ide.impl.jps.serialization.LoadedProjectData
import com.intellij.workspaceModel.ide.impl.jps.serialization.copyAndLoadProject
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files
import kotlin.io.path.exists

class WorkspaceCacheTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  private lateinit var serializer: EntityStorageSerializer

  @Before
  fun setUp() {
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
    serializer = EntityStorageSerializerImpl(WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver, virtualFileManager)
  }

  @After
  fun tearDown() {
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

    WorkspaceModelCache.getInstance(project)?.saveCacheNow()

    val project2 = loadProject(projectData.projectDir)

    val modules = ModuleManager.getInstance(project2).modules
    assertEquals(initialModulesSize, modules.size)
  }

  @Test
  fun `save cache for unloaded modules`() {
    val project = loadProject(prepareProject().projectDir)
    val cache = WorkspaceModelCache.getInstance(project) as WorkspaceModelCacheImpl
    cache.saveCacheNow()
    assertFalse("Cache for unloaded entities must not be created if no entities are unloaded",
                cache.getUnloadedEntitiesCacheFilePath().exists())

    runBlocking {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("newModule"))
    }

    cache.saveCacheNow()
    assertTrue(cache.getUnloadedEntitiesCacheFilePath().exists())
  }

  private fun prepareProject(): LoadedProjectData {
    val projectFile = projectFile("moduleAdded/after")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)
    val storage = projectData.storage

    val cacheFile = projectData.projectDir.resolve(cacheFileName()).toPath()
    Files.createFile(cacheFile)
    WorkspaceModelCacheImpl.testCacheFile = cacheFile

    serializer.serializeCache(cacheFile, storage)
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

    private val testProjectBase = "platform/workspace/jps/tests/testData/serialization/loadingFromCache"
    private val dirBasedProject = "$testProjectBase/directoryBased"

    private fun projectFile(path: String): File {
      return File(PathManagerEx.getCommunityHomePath(), "$dirBasedProject/$path")
    }

    private fun cacheFileName(): String {
      return "test_caching_" + RandomStringUtils.randomAlphabetic(5) + ".data"
    }
  }
}