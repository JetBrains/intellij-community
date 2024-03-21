// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.containers.DisposableWrapperList
import org.gradle.tooling.GradleConnectionException
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectModelContributor
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

abstract class GradlePhasedSyncTestCase : GradleImportingTestCase() {

  private lateinit var syncErrorHandler: AtomicReference<(String, String?) -> Unit>
  private val defaultSyncErrorHandler = { errorMessage: String, errorDetails: String? ->
    super.handleImportFailure(errorMessage, errorDetails)
  }

  override fun setUp() {
    super.setUp()

    myProject.registerServiceInstance(TestProjectResolverExtensionService::class.java, TestProjectResolverExtensionService())
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(TestProjectResolverExtension(), testRootDisposable)

    syncErrorHandler = AtomicReference(defaultSyncErrorHandler)
  }

  fun importProject(errorHandler: (String, String?) -> Unit) {
    syncErrorHandler.set(errorHandler)
    try {
      importProject()
    }
    finally {
      syncErrorHandler.set(defaultSyncErrorHandler)
    }
  }

  override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
    syncErrorHandler.get()(errorMessage, errorDetails)
  }

  fun addToolingExtensionClasses(parentDisposable: Disposable, vararg toolingExtensionClasses: Class<*>) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addToolingExtensionClasses(parentDisposable, toolingExtensionClasses.toList())
  }

  fun addProjectModelProviders(parentDisposable: Disposable, vararg modelProviders: ProjectImportModelProvider) {
    addProjectModelProviders(parentDisposable, modelProviders.toList())
  }

  fun addProjectModelProviders(parentDisposable: Disposable, modelProviders: Collection<ProjectImportModelProvider>) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addModelProviders(parentDisposable, modelProviders)
  }

  fun whenPhaseCompleted(parentDisposable: Disposable, action: (ProjectResolverContext, GradleModelFetchPhase) -> Unit) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addBuildActionListener(parentDisposable, object : TestBuildActionListener() {
        override fun onPhaseCompleted(phase: GradleModelFetchPhase) {
          action(resolverContext, phase)
        }
      })
  }

  fun whenProjectLoaded(parentDisposable: Disposable, action: (ProjectResolverContext) -> Unit) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addBuildActionListener(parentDisposable, object : TestBuildActionListener() {
        override fun onProjectLoaded() {
          action(resolverContext)
        }
      })
  }

  fun whenBuildCompleted(parentDisposable: Disposable, action: (ProjectResolverContext) -> Unit) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addBuildActionListener(parentDisposable, object : TestBuildActionListener() {
        override fun onBuildCompleted() {
          action(resolverContext)
        }
      })
  }

  fun addProjectModelContributor(parentDisposable: Disposable, contributor: ProjectModelContributor) {
    ProjectModelContributor.EP_NAME.point.registerExtension(contributor, parentDisposable)
  }

  fun initMultiModuleProject() {
    if (isGradleAtLeast("8.0")) {
      // For old Gradle versions, Idea sync project with build src in two sequent Gradle calls.
      // So don't use buildSrc for generic projects with multiple modules.
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

  fun assertMultiModuleProjectStructure() {
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
      if (isGradleAtLeast("8.0")) {
        addAll(buildSrcModules)
      }
      addAll(projectModules)
      addAll(includesProjectModules)
    })
  }

  private class TestProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getToolingExtensionsClasses(): Set<Class<*>> {
      val project = resolverCtx.externalSystemTaskId.findProject()!!
      return TestProjectResolverExtensionService.getInstance(project)
        .getToolingExtensionsClasses()
    }

    override fun getModelProviders(): List<ProjectImportModelProvider> {
      val project = resolverCtx.externalSystemTaskId.findProject()!!
      return TestProjectResolverExtensionService.getInstance(project)
        .getModelProviders()
    }

    override fun createBuildListener(): GradleBuildActionListener {
      val project = resolverCtx.externalSystemTaskId.findProject()!!
      return TestProjectResolverExtensionService.getInstance(project)
        .createBuildActionListener(resolverCtx)
    }
  }

  private class TestProjectResolverExtensionService {

    private val toolingExtensionClasses = DisposableWrapperList<Class<*>>()
    private val modelProviders = DisposableWrapperList<ProjectImportModelProvider>()
    private val buildActionListeners = DisposableWrapperList<TestBuildActionListener>()

    fun getToolingExtensionsClasses(): Set<Class<*>> {
      return toolingExtensionClasses.toSet()
    }

    fun addToolingExtensionClasses(parentDisposable: Disposable, toolingExtensionClasses: List<Class<*>>) {
      for (toolingExtensionClass in toolingExtensionClasses) {
        this.toolingExtensionClasses.add(toolingExtensionClass, parentDisposable)
      }
    }

    fun getModelProviders(): List<ProjectImportModelProvider> {
      return modelProviders
    }

    fun addModelProviders(parentDisposable: Disposable, modelProviders: Collection<ProjectImportModelProvider>) {
      for (modelProvider in modelProviders) {
        this.modelProviders.add(modelProvider, parentDisposable)
      }
    }

    fun createBuildActionListener(resolverContext: ProjectResolverContext): GradleBuildActionListener {
      buildActionListeners.forEach { it.resolverContext = resolverContext }
      return object : GradleBuildActionListener {

        override fun onPhaseCompleted(phase: GradleModelFetchPhase) {
          buildActionListeners.forEach { it.onPhaseCompleted(phase) }
        }

        override fun onProjectLoaded() {
          buildActionListeners.forEach { it.onProjectLoaded() }
        }

        override fun onBuildCompleted() {
          buildActionListeners.forEach { it.onBuildCompleted() }
        }

        override fun onBuildFailed(exception: GradleConnectionException) {
          buildActionListeners.forEach { it.onBuildFailed(exception) }
        }
      }
    }

    fun addBuildActionListener(parentDisposable: Disposable, listener: TestBuildActionListener) {
      buildActionListeners.add(listener, parentDisposable)
    }

    companion object {

      fun getInstance(project: Project): TestProjectResolverExtensionService {
        return project.service<TestProjectResolverExtensionService>()
      }
    }
  }

  private abstract class TestBuildActionListener : GradleBuildActionListener {

    lateinit var resolverContext: ProjectResolverContext
  }

  protected class ListenerAssertion {

    private val counter = AtomicInteger(0)
    private val failures = CopyOnWriteArrayList<Throwable>()

    fun trace(action: () -> Unit) {
      counter.incrementAndGet()
      try {
        return action()
      }
      catch (failure: Throwable) {
        failures.add(failure)
      }
    }

    fun assertListenerFailures() {
      runAll(failures) { throw it }
    }

    fun assertListenerState(expectedCount: Int, messageSupplier: () -> String) {
      Assertions.assertEquals(expectedCount, counter.get(), messageSupplier)
    }
  }
}