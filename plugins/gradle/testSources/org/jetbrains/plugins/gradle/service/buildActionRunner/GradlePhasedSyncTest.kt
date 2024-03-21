// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.buildActionRunner

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.importing.TestPhasedModel
import org.jetbrains.plugins.gradle.importing.TestPhasedModelProvider
import org.jetbrains.plugins.gradle.service.project.ProjectModelContributor
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class GradlePhasedSyncTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test one-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()

      addProjectModelProviders(disposable, TestPhasedModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
      whenBuildCompleted(disposable) { resolverContext ->
        buildCompletionAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
              val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
              Assertions.assertNotNull(buildFinishedModel) {
                "Expected BuildFinishedModel on the build completion"
              }
            }
          }
        }
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _, resolverContext ->
        projectModelContributorAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
              val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
              Assertions.assertNotNull(buildFinishedModel) {
                "Expected BuildFinishedModel in the project model contributor"
              }
            }
          }
        }
      })

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      buildCompletionAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(1) {
        "Build action should be finished only once"
      }
      projectModelContributorAssertion.assertListenerState(1) {
        "Project module contributor should be called only once"
      }
    }
  }

  @Test
  fun `test two-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()

      addProjectModelProviders(disposable, TestPhasedModelProvider(GradleModelFetchPhase.PROJECT_LOADED_PHASE))
      addProjectModelProviders(disposable, TestPhasedModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
      whenProjectLoaded(disposable) { resolverContext ->
        projectLoadingAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val projectLoadedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
              val projectLoadedModel = resolverContext.getProjectModel(projectModel, projectLoadedModelClass)
              Assertions.assertNotNull(projectLoadedModel) {
                "Expected ProjectLoadedModel on the project loaded phase"
              }
            }
          }
        }
      }
      whenBuildCompleted(disposable) { resolverContext ->
        buildCompletionAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val projectLoadedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
              val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
              val projectLoadedModel = resolverContext.getProjectModel(projectModel, projectLoadedModelClass)
              val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
              Assertions.assertNotNull(projectLoadedModel) {
                "Expected ProjectLoadedModel on the build completion"
              }
              Assertions.assertNotNull(buildFinishedModel) {
                "Expected BuildFinishedModel on the build completion"
              }
            }
          }
        }
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _, resolverContext ->
        projectModelContributorAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val projectLoadedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
              val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
              val projectLoadedModel = resolverContext.getProjectModel(projectModel, projectLoadedModelClass)
              val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
              Assertions.assertNotNull(projectLoadedModel) {
                "Expected ProjectLoadedModel in the project model contributor"
              }
              Assertions.assertNotNull(buildFinishedModel) {
                "Expected BuildFinishedModel in the project model contributor"
              }
            }
          }
        }
      })

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      projectLoadingAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) {
        "Project loaded phase should be finished only once"
      }
      buildCompletionAssertion.assertListenerState(1) {
        "Build action should be finished only once"
      }
      projectModelContributorAssertion.assertListenerState(1) {
        "Project module contributor should be called only once"
      }
    }
  }

  @Test
  fun `test multi-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()

      val phaseCompletionAssertion = ListenerAssertion()
      val completedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      val allPhases = GradleModelFetchPhase.entries
      val projectLoadedPhases = allPhases.filter { it <= GradleModelFetchPhase.PROJECT_LOADED_PHASE }
      val phasedModelProviders = allPhases.map { TestPhasedModelProvider(it) }

      addProjectModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { resolverContext, phase ->
        phaseCompletionAssertion.trace {
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
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
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
      addProjectModelContributor(disposable, ProjectModelContributor { _, resolverContext ->
        projectModelContributorAssertion.trace {
          Assertions.assertEquals(allPhases.toList(), completedPhases.toList()) {
            "All phases should be completed before running the project model contributor"
          }
        }
      })

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      projectLoadingAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerFailures()
      phaseCompletionAssertion.assertListenerFailures()
      phaseCompletionAssertion.assertListenerState(allPhases.size) {
        "All requested phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
    }
  }

  @Test
  fun `test one-phased Gradle sync cancellation`() {
    Disposer.newDisposable().use { disposable ->
      val isProjectModelContributorRunned = AtomicBoolean(false)
      val isSyncCancelled = AtomicBoolean(false)

      whenBuildCompleted(disposable) {
        throw ProcessCanceledException()
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _, _ ->
        isProjectModelContributorRunned.set(true)
      })

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        isSyncCancelled.set(true)
      })

      Assertions.assertTrue(isSyncCancelled.get()) {
        "Gradle sync should be cancelled during execution."
      }
      Assertions.assertFalse(isProjectModelContributorRunned.get()) {
        "Gradle sync should be cancelled during execution.\n" +
        "Therefore the project model contributor shouldn't be runned."
      }
    }
  }

  @Test
  fun `test two-phased Gradle sync cancellation`() {
    Disposer.newDisposable().use { disposable ->
      val isBuildFinished = AtomicBoolean(false)
      val isProjectModelContributorRunned = AtomicBoolean(false)
      val isSyncCancelled = AtomicBoolean(false)

      whenProjectLoaded(disposable) {
        throw ProcessCanceledException()
      }
      whenBuildCompleted(disposable) {
        isBuildFinished.set(true)
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _, _ ->
        isProjectModelContributorRunned.set(true)
      })

      initMultiModuleProject()
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
      Assertions.assertFalse(isProjectModelContributorRunned.get()) {
        "Gradle sync should be cancelled during the project loaded action.\n" +
        "Therefore the project model contributor shouldn't be runned."
      }
    }
  }

  @Test
  fun `test multi-phased Gradle sync cancellation during project loaded action`() {
    `test multi-phased Gradle sync cancellation`(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
  }

  @Test
  fun `test multi-phased Gradle sync cancellation during build finished action`() {
    `test multi-phased Gradle sync cancellation`(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
  }

  private fun `test multi-phased Gradle sync cancellation`(cancellationPhase: GradleModelFetchPhase) {
    Disposer.newDisposable().use { disposable ->
      val isProjectLoaded = AtomicBoolean(false)
      val isBuildCompleted = AtomicBoolean(false)
      val isProjectModelContributorRunned = AtomicBoolean(false)
      val isSyncCancelled = AtomicBoolean(false)

      val completedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      val allPhases = GradleModelFetchPhase.entries
      val phasedModelProviders = allPhases.map { TestPhasedModelProvider(it) }

      addProjectModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { _, phase ->
        if (phase == cancellationPhase) {
          throw ProcessCanceledException()
        }
        completedPhases.add(phase)
      }
      whenProjectLoaded(disposable) {
        isProjectLoaded.set(true)
      }
      whenBuildCompleted(disposable) {
        isBuildCompleted.set(true)
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _, _ ->
        isProjectModelContributorRunned.set(true)
      })

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        isSyncCancelled.set(true)
      })

      Assertions.assertTrue(isSyncCancelled.get()) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
      Assertions.assertFalse(isBuildCompleted.get()) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the project build action shouldn't be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
      Assertions.assertFalse(isProjectModelContributorRunned.get()) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the project model contributor shouldn't be runned.\n"
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
      if (cancellationPhase <= GradleModelFetchPhase.PROJECT_LOADED_PHASE) {
        Assertions.assertFalse(isProjectLoaded.get()) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the project loaded actions shouldn't be completed.\n" +
          "Requested phases = $allPhases\n" +
          "Completed phases = $completedPhases"
        }
      }
      else {
        Assertions.assertTrue(isProjectLoaded.get()) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the project loaded action should be completed.\n" +
          "Requested phases = $allPhases\n" +
          "Completed phases = $completedPhases"
        }
      }
      for (completedPhase in completedPhases) {
        Assertions.assertTrue(completedPhase < cancellationPhase) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the $completedPhase shouldn't be executed.\n" +
          "Requested phases = $allPhases\n" +
          "Completed phases = $completedPhases"
        }
      }
    }
  }
}