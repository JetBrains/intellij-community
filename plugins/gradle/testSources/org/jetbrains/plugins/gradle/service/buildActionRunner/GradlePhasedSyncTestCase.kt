// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

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
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

abstract class GradlePhasedSyncTestCase : GradleImportingTestCase() {

  override fun setUp() {
    super.setUp()
    myProject.registerServiceInstance(TestProjectResolverExtensionService::class.java, TestProjectResolverExtensionService())
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(TestProjectResolverExtension(), testRootDisposable)
  }

  fun addToolingExtensionClasses(parentDisposable: Disposable, vararg toolingExtensionClasses: Class<*>) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addToolingExtensionClasses(parentDisposable, toolingExtensionClasses.toList())
  }

  fun addProjectModelProviders(parentDisposable: Disposable, vararg modelProviders: ProjectImportModelProvider) {
    TestProjectResolverExtensionService.getInstance(myProject)
      .addModelProviders(parentDisposable, modelProviders.toList())
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

    fun addModelProviders(parentDisposable: Disposable, modelProviders: List<ProjectImportModelProvider>) {
      for (modelProvider in modelProviders) {
        this.modelProviders.add(modelProvider, parentDisposable)
      }
    }

    fun createBuildActionListener(resolverContext: ProjectResolverContext): GradleBuildActionListener {
      buildActionListeners.forEach { it.resolverContext = resolverContext }
      return object : GradleBuildActionListener {
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

    fun assertListenerState(expectedCount: Int, messageSupplier: () -> String) {
      runAll(
        { assertEquals(messageSupplier(), expectedCount, counter.get()) },
        { runAll(failures) { throw it } }
      )
    }
  }
}