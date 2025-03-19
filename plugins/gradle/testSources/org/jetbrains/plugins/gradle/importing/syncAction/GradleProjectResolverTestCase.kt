// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import java.util.concurrent.CopyOnWriteArrayList

abstract class GradleProjectResolverTestCase : GradleImportingTestCase() {

  fun whenResolveProjectInfoStarted(parentDisposable: Disposable, action: suspend (ProjectResolverContext, MutableEntityStorage) -> Unit) {
    GradleSyncContributor.EP_NAME.point.registerExtension(
      @Order(Int.MAX_VALUE)
      object : GradleSyncContributor {
        override suspend fun onResolveProjectInfoStarted(
          context: ProjectResolverContext,
          storage: MutableEntityStorage
        ) {
          action(context, storage)
        }
      }, parentDisposable)
  }

  fun whenPhaseCompleted(parentDisposable: Disposable, action: suspend (ProjectResolverContext, MutableEntityStorage, GradleModelFetchPhase) -> Unit) {
    GradleSyncContributor.EP_NAME.point.registerExtension(
      @Order(Int.MAX_VALUE)
      object : GradleSyncContributor {
        override suspend fun onModelFetchPhaseCompleted(
          context: ProjectResolverContext,
          storage: MutableEntityStorage,
          phase: GradleModelFetchPhase
        ) {
          action(context, storage, phase)
        }
      }, parentDisposable)
  }

  fun whenModelFetchCompleted(parentDisposable: Disposable, action: suspend (ProjectResolverContext, MutableEntityStorage) -> Unit) {
    GradleSyncContributor.EP_NAME.point.registerExtension(
      @Order(Int.MAX_VALUE)
      object : GradleSyncContributor {
        override suspend fun onModelFetchCompleted(
          context: ProjectResolverContext,
          storage: MutableEntityStorage
        ) {
          action(context, storage)
        }
      }, parentDisposable)
  }

  fun whenProjectLoaded(parentDisposable: Disposable, action: suspend (ProjectResolverContext, MutableEntityStorage) -> Unit) {
    GradleSyncContributor.EP_NAME.point.registerExtension(
      @Order(Int.MAX_VALUE)
      object : GradleSyncContributor {
        override suspend fun onProjectLoadedActionCompleted(
          context: ProjectResolverContext,
          storage: MutableEntityStorage
        ) {
          action(context, storage)
        }
      }, parentDisposable)
  }

  /**
   * Gradle project resolver recreates all extensions for sync.
   * Therefore, this function doesn't allow providing an instance of extension.
   * @see org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.createProjectResolvers
   */
  fun addProjectResolverExtension(
    projectResolverExtensionClass: Class<out AbstractTestProjectResolverExtension>,
    parentDisposable: Disposable,
    configure: AbstractTestProjectResolverService.() -> Unit
  ) {
    val projectResolverExtension = registerProjectResolverExtension(projectResolverExtensionClass, parentDisposable)
    val projectResolverService = registerProjectResolverService(projectResolverExtension.serviceClass, parentDisposable)
    projectResolverService.configure()
  }

  private fun <T : AbstractTestProjectResolverService> registerProjectResolverService(
    projectResolverServiceClass: Class<T>,
    parentDisposable: Disposable
  ): T {
    val projectResolverService = projectResolverServiceClass.getDeclaredConstructor().newInstance()
    myProject.registerOrReplaceServiceInstance(projectResolverServiceClass, projectResolverService, parentDisposable)
    return projectResolverService
  }

  private fun <T : AbstractTestProjectResolverExtension> registerProjectResolverExtension(
    projectResolverExtensionClass: Class<T>,
    parentDisposable: Disposable
  ): T {
    val projectResolverExtension = projectResolverExtensionClass.getDeclaredConstructor().newInstance()
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(projectResolverExtension, parentDisposable)
    return projectResolverExtension
  }

  /**
   * Creates the model multi-module Gradle project for generic Gradle sync testing.
   *
   * @param useBuildSrc is false for old Gradle versions.
   * The IDEA syncs a project with build src in two sequent Gradle calls.
   * Therefore, by default, we don't use buildSrc for the model project.
   *
   * @see assertMultiModuleProjectStructure
   */
  fun initMultiModuleProject(useBuildSrc: Boolean = isGradleAtLeast("8.0")) {
    if (useBuildSrc) {
      createBuildFile("buildSrc") {
        withPlugin("groovy")
        addImplementationDependency(code("gradleApi()"))
        addImplementationDependency(code("localGroovy()"))
      }
    }
    createSettingsFile {
      setProjectName("project")
      include("module")
      includeBuild("../includedProject")
    }
    createBuildFile {
      withJavaPlugin()
    }
    createBuildFile("module") {
      withJavaPlugin()
    }
    createSettingsFile("../includedProject") {
      setProjectName("includedProject")
      include("module")
    }
    createBuildFile("../includedProject") {
      withJavaPlugin()
    }
    createBuildFile("../includedProject/module") {
      withJavaPlugin()
    }
  }

  /**
   * Asserts the model multi-module Gradle project structure for generic Gradle sync testing.
   *
   * @param useBuildSrc is false for old Gradle versions.
   * The IDEA syncs a project with build src in two sequent Gradle calls.
   * Therefore, by default, we don't use buildSrc for the model project.
   *
   * @see initMultiModuleProject
   */
  fun assertMultiModuleProjectStructure(useBuildSrc: Boolean = isGradleAtLeast("8.0")) {
    val buildSrcModules = listOf(
      "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test"
    )
    val projectModules = listOf(
      "project", "project.main", "project.test",
      "project.module", "project.module.main", "project.module.test"
    )
    val includesProjectModules = listOf(
      "includedProject", "includedProject.main", "includedProject.test",
      "includedProject.module", "includedProject.module.main", "includedProject.module.test"
    )
    assertModules(buildList {
      if (useBuildSrc) {
        addAll(buildSrcModules)
      }
      addAll(projectModules)
      addAll(includesProjectModules)
    })
  }

  class TestProjectResolverExtension : AbstractTestProjectResolverExtension() {
    override val serviceClass = TestProjectResolverService::class.java
  }

  class TestProjectResolverService : AbstractTestProjectResolverService()

  abstract class AbstractTestProjectResolverExtension : AbstractProjectResolverExtension() {

    abstract val serviceClass: Class<out AbstractTestProjectResolverService>

    private fun getService(): AbstractTestProjectResolverService {
      val project = resolverCtx.externalSystemTaskId.findProject()!!
      return project.getService(serviceClass)
    }

    override fun getModelProviders(): List<ProjectImportModelProvider> {
      return getService().getModelProviders()
    }
  }

  abstract class AbstractTestProjectResolverService {

    private val modelProviders = CopyOnWriteArrayList<ProjectImportModelProvider>()

    fun getModelProviders(): List<ProjectImportModelProvider> {
      return modelProviders
    }

    fun addModelProviders(vararg modelProviders: ProjectImportModelProvider) {
      addModelProviders(modelProviders.toList())
    }

    fun addModelProviders(modelProviders: Collection<ProjectImportModelProvider>) {
      this.modelProviders.addAll(modelProviders)
    }
  }
}