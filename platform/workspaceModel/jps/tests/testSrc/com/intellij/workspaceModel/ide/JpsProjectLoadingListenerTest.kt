// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.JpsProjectLoadingManagerImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.jps.serialization.DelayedProjectSynchronizer
import com.intellij.workspaceModel.ide.impl.jps.serialization.LoadedProjectData
import com.intellij.workspaceModel.ide.impl.jps.serialization.copyAndLoadProject
import com.intellij.workspaceModel.storage.EntityStorageSerializer
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.apache.commons.lang.RandomStringUtils
import org.junit.*
import org.junit.Assert.assertTrue
import java.io.File

class JpsProjectLoadingListenerTest {
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
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
    serializer = EntityStorageSerializerImpl(WorkspaceModelCacheImpl.PluginAwareEntityTypesResolver, virtualFileManager)
  }

  @After
  fun tearDown() {
    WorkspaceModelCacheImpl.testCacheFile = null
  }

  @Test
  fun `test executing delayed task`() {
    val projectData = prepareProject()

    var listenerCalled = false
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded {
          listenerCalled = true
        }
      }
    })

    loadProject(projectData.projectDir)

    waitAndAssert(1_000, "Listener isn't called") {
      listenerCalled
    }
  }

  @Test
  fun `test listener right after project is loaded`() {
    val projectData = prepareProject()

    val project = loadProject(projectData.projectDir)
    waitAndAssert(1_000, "Project is not loaded") {
      (JpsProjectLoadingManager.getInstance(project) as JpsProjectLoadingManagerImpl).isProjectLoaded()
    }

    var listenerCalled = false
    JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded {
      listenerCalled = true
    }
    assertTrue(listenerCalled)
  }

  private fun prepareProject(): LoadedProjectData {
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
    DelayedProjectSynchronizer.backgroundPostStartupProjectLoading(project)
    return project
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

    private fun waitAndAssert(timeout: Int, message: String, action: () -> Boolean) {
      val initial = System.currentTimeMillis()
      while (System.currentTimeMillis() - initial < timeout) {
        if (action()) return
      }
      Assert.fail(message)
    }
  }
}