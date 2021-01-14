// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.*
import org.junit.Assert.assertTrue
import java.io.File

class CacheTest {
  @Rule
  @JvmField
  val projectModel = ProjectModelRule(true)

  @Rule
  @JvmField
  var disposableRule = DisposableRule()

  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    WorkspaceModelImpl.forceEnableCaching = true
    virtualFileManager = VirtualFileUrlManager.getInstance(projectModel.project)
  }

  @After
  fun tearDown() {
    WorkspaceModelImpl.forceEnableCaching = false
    WorkspaceModelCacheImpl.testCacheFile = null
  }

  @Test
  fun `test with caching after delay`() {
    Assume.assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val projectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)

    val project = loadProject(projectData.projectDir)

    waitAndAssert(5_000, "") {
      WorkspaceModel.getInstance(project).cache?.isCachingRequested == false
    }
    runInEdt {
      WriteAction.run<Throwable> {
        WorkspaceModel.getInstance(project).updateProjectModel {
          val moduleEntity = it.entities(ModuleEntity::class.java).first()
          it.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
            this.name = "AnotherName"
          }
        }
      }
    }

    waitAndAssert(1_000, "") { WorkspaceModel.getInstance(project).cache?.isCachingRequested == true }
    waitAndAssert(5_000, "") { WorkspaceModel.getInstance(project).cache?.isCachingRequested == false }
  }

  @Test
  fun `test with caching now`() {
    Assume.assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val projectFile = File(PathManagerEx.getCommunityHomePath(), "jps/model-serialization/testData/sampleProject")
    val projectData = copyAndLoadProject(projectFile, virtualFileManager)

    val project = loadProject(projectData.projectDir)

    waitAndAssert(5_000, "") {
      WorkspaceModel.getInstance(project).cache?.isCachingRequested == false
    }
    runInEdt {
      WriteAction.run<Throwable> {
        WorkspaceModel.getInstance(project).updateProjectModel {
          val moduleEntity = it.entities(ModuleEntity::class.java).first()
          it.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
            this.name = "AnotherName"
          }
        }
      }
    }

    waitAndAssert(1_000, "") { WorkspaceModel.getInstance(project).cache?.isCachingRequested == true }
    WorkspaceModel.getInstance(project).cache?.saveCacheNow()
    assertTrue(WorkspaceModel.getInstance(project).cache?.isCachingRequested == false)
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
    Assert.fail(message)
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
