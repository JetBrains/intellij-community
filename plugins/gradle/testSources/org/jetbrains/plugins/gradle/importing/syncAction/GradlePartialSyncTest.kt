// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.importing.TestModel
import org.jetbrains.plugins.gradle.importing.TestModelProvider
import org.jetbrains.plugins.gradle.service.project.GradlePartialResolverPolicy
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.junit.Test
import org.junit.jupiter.api.Assertions

class GradlePartialSyncTest : GradlePartialSyncTestCase() {

  @Test
  fun `test partial Gradle sync consistency`() {
    Disposer.newDisposable().use { disposable ->

      lateinit var resultResolverContext: ProjectResolverContext
      val modelFetchCompletionAssertion = ListenerAssertion()

      val defaultModelClass = TestModel.Model1::class.java
      val partialModelClass = TestModel.Model2::class.java
      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(TestModelProvider(defaultModelClass))
      }
      addProjectResolverExtension(TestPartialProjectResolverExtension::class.java, disposable) {
        addModelProviders(TestModelProvider(partialModelClass))
      }
      whenModelFetchCompleted(disposable) { resolverContext ->
        modelFetchCompletionAssertion.trace {
          resultResolverContext = resolverContext
        }
      }

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The model fetch action should be completed only once during the default Gradle sync"
      }
      modelFetchCompletionAssertion.reset()

      Assertions.assertNull(resultResolverContext.policy) {
        "Unexpected Gradle resolver policy for the default Gradle sync.\n" +
        "Resolver policy = ${resultResolverContext.policy}"
      }
      for (buildModel in resultResolverContext.allBuilds) {
        for (projectModel in buildModel.projects) {
          val defaultModel = resultResolverContext.getProjectModel(projectModel, defaultModelClass)
          Assertions.assertNotNull(defaultModel) {
            "Expected default model after the default Gradle sync"
          }
          val partialModel = resultResolverContext.getProjectModel(projectModel, partialModelClass)
          Assertions.assertNotNull(partialModel) {
            "Expected partial model after the default Gradle sync"
          }
        }
      }

      val projectDataStructure = getProjectDataStructure().graphCopy()

      importProject(importSpec = {
        projectResolverPolicy(GradlePartialResolverPolicy {
          it is TestPartialProjectResolverExtension
        })
      })

      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The model fetch action should be completed only once during the partial Gradle sync"
      }
      modelFetchCompletionAssertion.reset()

      Assertions.assertInstanceOf(GradlePartialResolverPolicy::class.java, resultResolverContext.policy) {
        "Expected Gradle partial resolver policy for the partial Gradle sync"
      }
      for (buildModel in resultResolverContext.allBuilds) {
        for (projectModel in buildModel.projects) {
          val defaultModel = resultResolverContext.getProjectModel(projectModel, defaultModelClass)
          Assertions.assertNull(defaultModel) {
            "Unexpected default model after the partial Gradle sync"
          }
          val partialModel = resultResolverContext.getProjectModel(projectModel, partialModelClass)
          Assertions.assertNotNull(partialModel) {
            "Expected partial model after the partial Gradle sync"
          }
        }
      }

      assertMultiModuleProjectStructure()
      Assertions.assertEquals(projectDataStructure, getProjectDataStructure()) {
        "The project data structure shouldn't be changed after the partial Gradle sync"
      }
    }
  }
}