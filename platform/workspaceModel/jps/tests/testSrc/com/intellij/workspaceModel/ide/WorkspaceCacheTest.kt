// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer
import com.intellij.workspaceModel.ide.impl.jps.serialization.LoadedProjectData
import com.intellij.workspaceModel.ide.impl.jps.serialization.copyAndLoadProject
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorageSerializer
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang.RandomStringUtils
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
    serializer = EntityStorageSerializerImpl(WorkspaceModelCacheSerializer.PluginAwareEntityTypesResolver, virtualFileManager,
                                             WorkspaceModelCacheSerializer::collectExternalCacheVersions)
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
    assertFalse("Cache for unloaded entities must not be created if no entities are unloaded", cache.getUnloadedEntitiesCacheFilePath().exists())
    
    runBlocking {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("newModule"))
    }
    
    cache.saveCacheNow()
    assertTrue(cache.getUnloadedEntitiesCacheFilePath().exists())
  }

  @Test
  fun `custom version provider`() {
    val projectData = prepareProject()

    val project = loadProject(projectData.projectDir)

    ExtensionTestUtil.maskExtensions(WORKSPACE_MODEL_CACHE_VERSION_EP, listOf(VersionOne), project)

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel {
          it.addModuleEntity("MyTestModule", emptyList(), MySource)
        }
      }
    }

    WorkspaceModelCache.getInstance(project)?.saveCacheNow()

    val project2 = loadProject(projectData.projectDir)

    val modules = WorkspaceModel.getInstance(project2).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertTrue(modules.any { it.name == "MyTestModule" })
  }

  @Test
  fun `custom version provider with changed version`() {
    val projectData = prepareProject()

    val project = loadProject(projectData.projectDir)

    val pointDisposable = Disposer.newDisposable()
    ExtensionTestUtil.maskExtensions(WORKSPACE_MODEL_CACHE_VERSION_EP, listOf(VersionOne), pointDisposable)

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel {
          it.addModuleEntity("MyTestModule", emptyList(), MySource)
        }
      }
    }

    WorkspaceModelCache.getInstance(project)?.saveCacheNow()

    Disposer.dispose(pointDisposable)
    val anotherPointDisposable = Disposer.newDisposable(project, "Point disposable")
    ExtensionTestUtil.maskExtensions(WORKSPACE_MODEL_CACHE_VERSION_EP, listOf(VersionTwo), anotherPointDisposable)
    val project2 = loadProject(projectData.projectDir)

    val modules = WorkspaceModel.getInstance(project2).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertFalse(modules.any { it.name == "MyTestModule" })
  }

  object MySource: EntitySource

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

  private object VersionOne : WorkspaceModelCacheVersion {
    override fun getId(): String = "test"

    override fun getVersion(): String = "1"
  }

  private object VersionTwo : WorkspaceModelCacheVersion {
    override fun getId(): String = "test"

    override fun getVersion(): String = "2"
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    private val testProjectBase = "platform/workspaceModel/jps/tests/testData/serialization/loadingFromCache"
    private val dirBasedProject = "$testProjectBase/directoryBased"

    private fun projectFile(path: String): File {
      return File(PathManagerEx.getCommunityHomePath(), "$dirBasedProject/$path")
    }

    private fun cacheFileName(): String {
      return "test_caching_" + RandomStringUtils.randomAlphabetic(5) + ".data"
    }
    private val WORKSPACE_MODEL_CACHE_VERSION_EP = ExtensionPointName.create<WorkspaceModelCacheVersion>("com.intellij.workspaceModel.cache.version")
  }
}