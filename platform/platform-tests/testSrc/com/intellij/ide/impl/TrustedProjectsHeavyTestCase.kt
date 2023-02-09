// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.io.NioPathPrefixTreeFactory
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.toVirtualFileUrl
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
abstract class TrustedProjectsHeavyTestCase {

  @TestDisposable
  lateinit var testDisposable: Disposable

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = fileFixture.tempDirPath.toNioPath().refreshAndGetVirtualDirectory()
  }

  @AfterEach
  fun tearDown() {
    fileFixture.tearDown()
  }

  suspend fun createProjectAsync(
    relativeProjectRoot: String
  ): Project {
    val projectRoot = writeAction {
      testRoot.createDirectory(relativeProjectRoot)
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
        testRoot.findOrCreateDirectory(it)
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
    return project
  }

  private fun generateModulesAsync(
    project: Project,
    entityStorage: MutableEntityStorage,
    relativeProjectRoot: String,
    numModules: Int,
    numContentRoots: Int
  ) {
    val projectRoot = testRoot.findOrCreateDirectory(relativeProjectRoot)
    generateModuleAsync(project, entityStorage, projectRoot, numContentRoots)
    repeat(numModules - 1) {
      val moduleRoot = projectRoot.createDirectory(projectRoot.name + "-module-$it")
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
      contentRoots.add(moduleRoot.createDirectory("contentRoot-$it"))
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
    val locatedProject = TrustedProjectsLocator.locateProject(project)
    Assertions.assertEquals(
      relativeRoots.map { testRoot.toNioPath().getResolvedPath(it) }.toSet(),
      locatedProject.projectRoots.toSet()
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

  class TestTrustedProjectsLocator : TrustedProjectsLocator {

    override fun getProjectRoots(project: Project): List<Path> {
      val index = NioPathPrefixTreeFactory.createSet()
      for (module in project.modules) {
        for (contentRoot in module.rootManager.contentRoots) {
          index.add(contentRoot.toNioPath())
        }
      }
      return index.getRoots().toList()
    }

    override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> {
      return emptyList()
    }
  }
}