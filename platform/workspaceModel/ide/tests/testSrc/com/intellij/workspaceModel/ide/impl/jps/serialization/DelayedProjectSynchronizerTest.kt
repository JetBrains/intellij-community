// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.*
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.storage.EntityStorageSerializer
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import org.apache.commons.lang.RandomStringUtils
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import java.io.File

class DelayedProjectSynchronizerTest {
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
  fun `test just loading with existing cache`() {
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val projectFile = projectFile("moduleAdded/after")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)
    val storage = projectData.storage

    val cacheFile = projectData.projectDir.resolve(cacheFileName())
    cacheFile.createNewFile()
    WorkspaceModelCacheImpl.testCacheFile = cacheFile

    cacheFile.outputStream().use {
      serializer.serializeCache(it, storage)
    }

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
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val projectFile = projectFile("moduleAdded/before")
    val projectFileAfter = projectFile("moduleAdded/after")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)
    val storage = projectData.storage

    val cacheFile = projectData.projectDir.resolve(cacheFileName())
    cacheFile.createNewFile()
    WorkspaceModelCacheImpl.testCacheFile = cacheFile

    assertTrue("xxx" !in storage.entities(ModuleEntity::class.java).map { it.name })

    cacheFile.outputStream().use {
      serializer.serializeCache(it, storage)
    }

    val (afterProjectFiles, _) = copyProjectFiles(projectFileAfter)
    val project = loadProject(afterProjectFiles)

    waitAndAssert(1_000, "xxx module isn't found") {
      "xxx" in ModuleManager.getInstance(project).modules.map { it.name }
    }

    assertTrue((WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache)
    checkSerializersConsistency(project)
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