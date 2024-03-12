// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.importing.TestPhasedModel
import org.jetbrains.plugins.gradle.importing.TestPhasedModelProvider
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class GradlePhasedSyncTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test one-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val buildCompletionAssertion = ListenerAssertion()

      addProjectModelProviders(disposable, TestPhasedModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
      whenBuildCompleted(disposable) { resolverContext ->
        buildCompletionAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()
          val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
          val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
          Assertions.assertNotNull(buildFinishedModel) { "Expected BuildFinishedModel on the build completion" }
        }
      }

      createSettingsFile("")
      importProject()

      buildCompletionAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(1) { "Build action should be finished only once" }
    }
  }

  @Test
  fun `test two-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()

      addProjectModelProviders(disposable, TestPhasedModelProvider(GradleModelFetchPhase.PROJECT_LOADED_PHASE))
      addProjectModelProviders(disposable, TestPhasedModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
      whenProjectLoaded(disposable) { resolverContext ->
        projectLoadingAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()
          val projectLoadedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
          val projectLoadedModel = resolverContext.getProjectModel(projectModel, projectLoadedModelClass)
          Assertions.assertNotNull(projectLoadedModel) { "Expected ProjectLoadedModel on the project loaded phase" }
        }
      }
      whenBuildCompleted(disposable) { resolverContext ->
        buildCompletionAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()
          val projectLoadedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
          val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
          val projectLoadedModel = resolverContext.getProjectModel(projectModel, projectLoadedModelClass)
          val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
          Assertions.assertNotNull(projectLoadedModel) { "Expected ProjectLoadedModel on the build completion" }
          Assertions.assertNotNull(buildFinishedModel) { "Expected BuildFinishedModel on the build completion" }
        }
      }

      createSettingsFile("")
      importProject()

      projectLoadingAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) { "Project loaded phase should be finished only once" }
      buildCompletionAssertion.assertListenerState(1) { "Build action should be finished only once" }
    }
  }

  @Test
  fun `test multi-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()

      val phaseCompletionAssertion = ListenerAssertion()
      val completedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      val allPhases = GradleModelFetchPhase.entries
      val projectLoadedPhases = allPhases.filter { it <= GradleModelFetchPhase.PROJECT_LOADED_PHASE }
      val phasedModelProviders = allPhases.map { TestPhasedModelProvider(it) }

      addProjectModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { resolverContext, phase ->
        phaseCompletionAssertion.trace {
          val buildModel = resolverContext.allBuilds.single()
          val projectModel = buildModel.projects.single()

          for (completedPhase in completedPhases) {
            Assertions.assertTrue(completedPhase < phase) {
              "The $phase should be completed before the $completedPhase.\n" +
              "Requested phases = $allPhases\n" +
              "Completed phases = $completedPhases"
            }
          }
          Assertions.assertTrue(completedPhases.add(phase)) {
            "The $phase should be finished only once.\n" +
            "Requested phases = $allPhases\n" +
            "Completed phases = $completedPhases"
          }
          for (completedPhase in completedPhases) {
            val phasedModelClass = TestPhasedModel.getModelClass(completedPhase)
            val phasedModel = resolverContext.getProjectModel(projectModel, phasedModelClass)
            Assertions.assertNotNull(phasedModel) {
              "Expected model for the $completedPhase on the $phase completion.\n" +
              "Requested phases = $allPhases\n" +
              "Completed phases = $completedPhases"
            }
          }
        }
      }
      whenProjectLoaded(disposable) {
        projectLoadingAssertion.trace {
          Assertions.assertEquals(projectLoadedPhases.toList(), completedPhases.toList()) {
            "All project loaded phases should be completed before finishing the project loaded action"
          }
        }
      }
      whenBuildCompleted(disposable) {
        buildCompletionAssertion.trace {
          Assertions.assertEquals(allPhases.toList(), completedPhases.toList()) {
            "All phases should be completed before finishing the build action"
          }
        }
      }

      createSettingsFile("")
      importProject()

      projectLoadingAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerFailures()
      phaseCompletionAssertion.assertListenerFailures()
      phaseCompletionAssertion.assertListenerState(allPhases.size) {
        "All requested phases should be completed.\n" +
        "Requested phases = $allPhases\n"
        "Completed phases = $completedPhases"
      }
    }
  }

  @Test
  fun `test one-phased Gradle sync cancellation`() {
    Disposer.newDisposable().use { disposable ->
      val isSyncCancelled = AtomicBoolean(false)

      whenBuildCompleted(disposable) {
        throw ProcessCanceledException()
      }

      createSettingsFile("")
      importProject(errorHandler = { _, _ ->
        isSyncCancelled.set(true)
      })

      Assertions.assertTrue(isSyncCancelled.get()) {
        "Gradle sync should be cancelled."
      }
    }
  }

  @Test
  fun `test two-phased Gradle sync cancellation`() {
    Disposer.newDisposable().use { disposable ->
      val isBuildFinished = AtomicBoolean(false)
      val isSyncCancelled = AtomicBoolean(false)

      whenProjectLoaded(disposable) {
        throw ProcessCanceledException()
      }
      whenBuildCompleted(disposable) {
        isBuildFinished.set(true)
      }

      createSettingsFile("")
      importProject(errorHandler = { _, _ ->
        isSyncCancelled.set(true)
      })

      Assertions.assertTrue(isSyncCancelled.get()) {
        "Gradle sync should be cancelled during the project loaded action."
      }
      Assertions.assertFalse(isBuildFinished.get()) {
        "Gradle sync should be cancelled during the project loaded action.\n" +
        "Therefore the project build action shouldn't be completed."
      }
    }
  }
}