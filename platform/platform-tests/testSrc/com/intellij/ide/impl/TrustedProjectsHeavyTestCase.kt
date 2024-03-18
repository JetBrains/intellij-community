// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
abstract class TrustedProjectsHeavyTestCase {

  @TestDisposable
  private lateinit var testDisposable: Disposable

  private lateinit var fileFixture: TempDirTestFixture
  private lateinit var testRoot: VirtualFile
  private lateinit var testPath: Path

  @BeforeEach
  fun setUp() {
    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()

    testPath = Path.of(fileFixture.tempDirPath)
    testRoot = testPath.refreshAndGetVirtualDirectory()
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
        it.applyChangesFrom(entityStorage)
      }
    }
  }

  suspend fun generateProjectAsync(
    project: Project,
    numModules: Int,
    numContentRoots: Int
  ): Project {
    val projectName = project.name
    writeAction {
      val entityStorage = MutableEntityStorage.create()
      generateModuleAsync(project, entityStorage, "project", numContentRoots)
      repeat(numModules - 1) { index ->
        generateModuleAsync(project, entityStorage, "module-$index", numContentRoots)
      }
      WorkspaceModel.getInstance(project).updateProjectModel("Generate project $projectName") {
        it.applyChangesFrom(entityStorage)
      }
    }
    return project
  }

  private fun generateModuleAsync(
    project: Project,
    entityStorage: MutableEntityStorage,
    relativePath: String,
    numContentRoots: Int
  ) {
    val contentRoots = ArrayList<VirtualFile>()
    val moduleRoot = testRoot.findOrCreateDirectory(relativePath)
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
    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val moduleEntity = ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource) {
      this.contentRoots = contentRoots.map {
        ContentRootEntity.invoke(it.toVirtualFileUrl(fileUrlManager), emptyList(), NonPersistentEntitySource)
      }
    }
    entityStorage.addEntity(moduleEntity)
  }

  fun assertProjectRoots(project: Project, vararg relativeRoots: String) {
    val locatedProject = TrustedProjectsLocator.locateProject(project)
    Assertions.assertEquals(
      relativeRoots.map { testPath.getResolvedPath(it) }.toSet(),
      locatedProject.projectRoots.toSet()
    )
  }

  fun generatePaths(relativePath: String, numPaths: Int): List<Path> {
    return generatePaths(listOf(testPath), relativePath, numPaths)
  }

  fun generatePaths(roots: List<Path>, relativePath: String, numPaths: Int): List<Path> {
    val paths = ArrayList<Path>()
    for (root in roots) {
      paths.add(root.getResolvedPath(relativePath))
      repeat(numPaths - 1) { index ->
        paths.add(root.getResolvedPath("$relativePath-$index"))
      }
    }
    return paths
  }

  inline fun <R> testPerformance(name: String, maxDuration: Duration? = null, action: () -> R): R {
    val start = System.currentTimeMillis()
    val result = action()
    val end = System.currentTimeMillis()
    val duration = (end - start).milliseconds
    println("$name duration is $duration")
    if (maxDuration != null && maxDuration < duration) {
      val percentage = duration.inWholeMilliseconds * 100 / maxDuration.inWholeMilliseconds - 100
      throw AssertionError("Execution time exceeded by $percentage% ($duration instead $maxDuration).")
    }
    return result
  }

  fun registerProjectLocator(projectLocator: TrustedProjectsLocator) {
    TrustedProjectsLocator.EP_NAME.point.registerExtension(projectLocator, testDisposable)
  }
}