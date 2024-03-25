// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.importing.TestPhasedModel
import org.jetbrains.plugins.gradle.importing.TestModelProvider
import org.jetbrains.plugins.gradle.service.project.ProjectModelContributor
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CopyOnWriteArrayList

class GradlePhasedSyncTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test one-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()

      addModelProviders(disposable, TestModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
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
      addProjectModelContributor(disposable, ProjectModelContributor { resolverContext ->
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

      addModelProviders(disposable, TestModelProvider(GradleModelFetchPhase.PROJECT_LOADED_PHASE))
      addModelProviders(disposable, TestModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
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
      addProjectModelContributor(disposable, ProjectModelContributor { resolverContext ->
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
      projectLoadingAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
      buildCompletionAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(1) {
        "The build action should be finished only once"
      }
      projectModelContributorAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerState(1) {
        "The project module contributor should be called only once"
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

      val allPhases = GradleModelFetchPhase.entries
      val phasedModelProviders = allPhases.map { TestModelProvider(it) }
      val projectLoadedPhases = allPhases.filter { it <= GradleModelFetchPhase.PROJECT_LOADED_PHASE }
      val completedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      addModelProviders(disposable, phasedModelProviders)
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
      addProjectModelContributor(disposable, ProjectModelContributor { resolverContext ->
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
      projectLoadingAssertion.assertListenerState(1) {
        "The project loaded action should be completed"
      }
      buildCompletionAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(1) {
        "The build finished action should be completed"
      }
      projectModelContributorAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerState(1) {
        "The model fetch action should be completed"
      }
      phaseCompletionAssertion.assertListenerFailures()
      phaseCompletionAssertion.assertListenerState(allPhases.size) {
        "All requested model fetch phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
      Assertions.assertEquals(allPhases, completedPhases) {
        "All requested model fetch phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
    }
  }

  @Test
  fun `test one-phased Gradle sync cancellation`() {
    Disposer.newDisposable().use { disposable ->
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()

      whenBuildCompleted(disposable) {
        buildCompletionAssertion.touch()
        throw ProcessCanceledException()
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _ ->
        projectModelContributorAssertion.touch()
      })

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      buildCompletionAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(1) {
        "The build finished action should be completed"
      }
      projectModelContributorAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerState(0) {
        "The Gradle sync should be cancelled during execution.\n" +
        "Therefore the project model contributor shouldn't be runned."
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution."
      }
    }
  }

  @Test
  fun `test two-phased Gradle sync cancellation`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()

      whenProjectLoaded(disposable) {
        projectLoadingAssertion.touch()
        throw ProcessCanceledException()
      }
      whenBuildCompleted(disposable) {
        buildCompletionAssertion.touch()
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _ ->
        projectModelContributorAssertion.touch()
      })

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      projectLoadingAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) {
        "The Gradle project loaded action should be completed."
      }
      buildCompletionAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(0) {
        "The Gradle sync should be cancelled during the project loaded action.\n" +
        "Therefore the build finished action shouldn't be completed."
      }
      projectModelContributorAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerState(0) {
        "The Gradle sync should be cancelled during the project loaded action.\n" +
        "Therefore the project model contributor shouldn't be runned."
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution."
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
      val projectLoadingAssertion = ListenerAssertion()
      val buildCompletionAssertion = ListenerAssertion()
      val projectModelContributorAssertion = ListenerAssertion()
      val phaseCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()

      val allPhases = GradleModelFetchPhase.entries
      val phasedModelProviders = allPhases.map { TestModelProvider(it) }
      val expectedCompletedPhases = allPhases.filter { it <= cancellationPhase }
      val actualCompletedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      addModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { _, phase ->
        phaseCompletionAssertion.touch()
        actualCompletedPhases.add(phase)
        if (phase == cancellationPhase) {
          throw ProcessCanceledException()
        }
      }
      whenProjectLoaded(disposable) {
        projectLoadingAssertion.touch()
      }
      whenBuildCompleted(disposable) {
        buildCompletionAssertion.touch()
      }
      addProjectModelContributor(disposable, ProjectModelContributor { _ ->
        projectModelContributorAssertion.touch()
      })

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      if (cancellationPhase <= GradleModelFetchPhase.PROJECT_LOADED_PHASE) {
        projectLoadingAssertion.assertListenerFailures()
        projectLoadingAssertion.assertListenerState(0) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the project loaded action shouldn't be completed.\n" +
          "Requested phases = $allPhases\n" +
          "Expected completed phases = $expectedCompletedPhases\n" +
          "Actual completed phases = $actualCompletedPhases\n"
        }
      }
      else {
        projectLoadingAssertion.assertListenerFailures()
        projectLoadingAssertion.assertListenerState(1) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the project loaded action should be completed.\n" +
          "Requested phases = $allPhases\n" +
          "Expected completed phases = $expectedCompletedPhases\n" +
          "Actual completed phases = $actualCompletedPhases\n"
        }
      }
      buildCompletionAssertion.assertListenerFailures()
      buildCompletionAssertion.assertListenerState(0) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the build finished action shouldn't be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases\n"
      }
      projectModelContributorAssertion.assertListenerFailures()
      projectModelContributorAssertion.assertListenerState(0) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the project model contributor shouldn't be runned.\n"
        "Requested phases = $allPhases\n" +
        "Completed phases = $actualCompletedPhases\n" +
        "Expected phases = $expectedCompletedPhases"
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases\n"
      }
      phaseCompletionAssertion.assertListenerFailures()
      phaseCompletionAssertion.assertListenerState(expectedCompletedPhases.size) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the earliest model fetch phases should be completed.\n"
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases\n"
      }
      Assertions.assertEquals(expectedCompletedPhases, actualCompletedPhases) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases\n"
      }
    }
  }
}