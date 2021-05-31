// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.readText
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.storage.EntityStorageSerializer
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.apache.commons.lang.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.streams.toList

class DelayedProjectSynchronizerTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule(true)

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val tempDirectory = TempDirectory()

  private lateinit var virtualFileManager: VirtualFileUrlManager
  private lateinit var serializer: EntityStorageSerializer

  @Before
  fun setUp() {
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
    serializer = EntityStorageSerializerImpl(WorkspaceModelCacheImpl.PluginAwareEntityTypesResolver, virtualFileManager)
  }

  @After
  fun tearDown() {
    WorkspaceModelCacheImpl.testCacheFile = null
  }

  @Test
  fun `test just loading with existing cache`() {
    val projectFile = projectFile("moduleAdded/after")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)
    saveToCache(projectData.storage)

    val project = loadProject(projectData.projectDir)

    val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelImpl
    assertTrue(workspaceModel.loadedFromCache)
    checkSerializersConsistency(project)
  }

  private fun checkSerializersConsistency(project: Project) {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    val serializers = JpsProjectModelSynchronizer.getInstance(project)!!.getSerializers()
    serializers.checkConsistency(getJpsProjectConfigLocation(project)!!.baseDirectoryUrlString, storage, VirtualFileUrlManager.getInstance(project))
  }

  @Test
  fun `test module added`() {
    val projectFile = projectFile("moduleAdded/before")
    val projectFileAfter = projectFile("moduleAdded/after")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)
    val storage = projectData.storage

    assertTrue("xxx" !in storage.entities(ModuleEntity::class.java).map { it.name })

    saveToCache(storage)

    val (afterProjectFiles, _) = copyProjectFiles(projectFileAfter)
    val project = loadProject(afterProjectFiles)

    waitAndAssert(1_000, "xxx module isn't found") {
      "xxx" in ModuleManager.getInstance(project).modules.map { it.name }
    }

    assertTrue((WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache)
    checkSerializersConsistency(project)
  }

  @Test
  fun `add library to project loaded from cache`() {
    val projectData = copyAndLoadProject(sampleDirBasedProjectFile, virtualFileManager)
    saveToCache(projectData.storage)

    //we reset 'nextId' property to emulate JVM restart; after we decouple serialization logic from IDE classes (as part of IDEA-252970),
    //it'll be possible to create a proper tests which prepares the cache in a separate JVM process
    JpsFileEntitySource.FileInDirectory.resetId()

    val project = loadProject(projectData.projectDir)
    runWriteActionAndWait {
      LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary("foo")
    }
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    JpsProjectModelSynchronizer.getInstance(project)!!.getSerializers().saveAllEntities(storage, projectData.projectDir)
    val librariesFolder = projectData.projectDir.toPath().resolve(".idea/libraries/")
    val librariesPaths = Files.list(librariesFolder).sorted().toList()
    assertEquals(4, librariesPaths.size)
    assertThat(librariesPaths.map { it.name }).containsAll(listOf("foo.xml", "jarDir.xml", "junit.xml", "log4j.xml"))
    assertTrue(librariesPaths[0].readText().contains("library name=\"foo\""))
    assertTrue(librariesPaths[1].readText().contains("library name=\"jarDir\""))
    assertTrue(librariesPaths[2].readText().contains("library name=\"junit\""))
    assertTrue(librariesPaths[3].readText().contains("library name=\"log4j\""))
  }

  private fun saveToCache(storage: WorkspaceEntityStorage) {
    val cacheFile = tempDirectory.newFile("cache.data")
    WorkspaceModelCacheImpl.testCacheFile = cacheFile


    cacheFile.outputStream().use {
      serializer.serializeCache(it, storage)
    }
  }

  private fun loadProject(projectDir: File): Project {
    val project = PlatformTestUtil.loadAndOpenProject(projectDir.toPath(), disposableRule.disposable)
    Disposer.register(disposableRule.disposable, Disposable {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    })
    return project
  }

  private fun waitAndAssert(timeout: Int, message: String, action: () -> Boolean) {
    val initial = System.currentTimeMillis()
    while (System.currentTimeMillis() - initial < timeout) {
      if (action()) return
    }
    fail(message)
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