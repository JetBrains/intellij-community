// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.file.VirtualFileUtil
import com.intellij.openapi.file.VirtualFileUtil.getAbsoluteNioPath
import com.intellij.openapi.file.system.LocalFileSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.withProjectAsync
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
abstract class TrustedProjectsHeavyTestCase {

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = LocalFileSystemUtil.getDirectory(fileFixture.tempDirPath)
  }

  @AfterEach
  fun tearDown() {
    fileFixture.tearDown()
  }

  suspend fun createProjectAsync(
    relativeProjectRoot: String
  ): Project {
    val projectRoot = writeAction {
      VirtualFileUtil.createDirectory(testRoot, relativeProjectRoot)
    }
    val projectManager = ProjectManagerEx.getInstanceEx()
    return closeOpenedProjectsIfFailAsync {
      projectManager.newProjectAsync(projectRoot.toNioPath(), OpenProjectTask {
        isNewProject = true
        runConfigurators = true
        projectName = projectRoot.name
      })
    }
  }

  suspend fun createModuleAsync(
    project: Project,
    moduleName: String,
    vararg relativeContentRoots: String
  ) {
    writeAction {
      val entityStorage = MutableEntityStorage.create()
      val contentRoots = relativeContentRoots.map {
        VirtualFileUtil.findOrCreateDirectory(testRoot, it)
      }
      addModuleEntity(project, entityStorage, moduleName, contentRoots)
      WorkspaceModel.getInstance(project).updateProjectModel("Create module $moduleName") {
        it.addDiff(entityStorage)
      }
    }
  }

  suspend fun generateProjectAsync(
    project: Project,
    numProjects: Int,
    numModules: Int,
    numContentRoots: Int
  ): Project {
    val projectName = project.name
    project.withProjectAsync {
      writeAction {
        val entityStorage = MutableEntityStorage.create()
        generateModulesAsync(project, entityStorage, "project", numModules, numContentRoots)
        repeat(numProjects - 1) { index ->
          generateModulesAsync(project, entityStorage, "project-$index", numModules, numContentRoots)
        }
        WorkspaceModel.getInstance(project).updateProjectModel("Generate project $projectName") {
          it.addDiff(entityStorage)
        }
      }
    }
    return project
  }

  private fun generateModulesAsync(
    project: Project,
    entityStorage: MutableEntityStorage,
    relativeProjectRoot: String,
    numModules: Int,
    numContentRoots: Int
  ) {
    val projectRoot = VirtualFileUtil.findOrCreateDirectory(testRoot, relativeProjectRoot)
    generateModuleAsync(project, entityStorage, projectRoot, numContentRoots)
    repeat(numModules - 1) {
      val moduleRoot = VirtualFileUtil.createDirectory(projectRoot, projectRoot.name + "-module-$it")
      generateModuleAsync(project, entityStorage, moduleRoot, numContentRoots)
    }
  }

  private fun generateModuleAsync(
    project: Project,
    entityStorage: MutableEntityStorage,
    moduleRoot: VirtualFile,
    numContentRoots: Int
  ) {
    val contentRoots = ArrayList<VirtualFile>()
    contentRoots.add(moduleRoot)
    repeat(numContentRoots - 1) {
      contentRoots.add(VirtualFileUtil.createDirectory(moduleRoot, "contentRoot-$it"))
    }
    addModuleEntity(project, entityStorage, moduleRoot.name, contentRoots)
  }

  private fun addModuleEntity(
    project: Project,
    entityStorage: MutableEntityStorage,
    moduleName: String,
    contentRoots: List<VirtualFile>
  ) {
    val fileUrlManager = VirtualFileUrlManager.getInstance(project)
    val moduleEntity = ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource) {
      this.contentRoots = contentRoots.map {
        ContentRootEntity.invoke(it.toVirtualFileUrl(fileUrlManager), emptyList(), NonPersistentEntitySource)
      }
    }
    entityStorage.addEntity(moduleEntity)
  }

  fun assertProjectRoots(
    project: Project,
    vararg relativeRoots: String
  ) {
    Assertions.assertEquals(
      relativeRoots.map { testRoot.getAbsoluteNioPath(it) }.toSet(),
      project.getProjectRoots().toSet()
    )
  }

  inline fun <R> testPerformance(name: String, maxDuration: Duration? = null, action: () -> R): R {
    val start = System.currentTimeMillis()
    val result = action()
    val end = System.currentTimeMillis()
    val duration = (end - start).milliseconds
    if (maxDuration != null && maxDuration < duration) {
      val percentage = duration.inWholeMilliseconds * 100 / maxDuration.inWholeMilliseconds - 100
      throw AssertionError("Execution time exceeded by $percentage% ($duration instead $maxDuration).")
    }
    println("$name duration is $duration")
    return result
  }
}