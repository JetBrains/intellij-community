// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import kotlinx.coroutines.delay
import org.jetbrains.plugins.gradle.importing.TestModelProvider
import org.jetbrains.plugins.gradle.importing.TestPhasedModel
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskFinished
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskStarted
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class GradlePhasedSyncTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test one-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val modelFetchCompletionAssertion = ListenerAssertion()

      addModelProviders(disposable, TestModelProvider(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE))
      whenModelFetchCompleted(disposable) { resolverContext ->
        modelFetchCompletionAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
              val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
              Assertions.assertNotNull(buildFinishedModel) {
                "Expected BuildFinishedModel on the model fetch action completion"
              }
            }
          }
        }
      }

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The model fetch action should be completed only once"
      }
    }
  }

  @Test
  fun `test two-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()

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
      whenModelFetchCompleted(disposable) { resolverContext ->
        modelFetchCompletionAssertion.trace {
          for (buildModel in resolverContext.allBuilds) {
            for (projectModel in buildModel.projects) {
              val projectLoadedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
              val buildFinishedModelClass = TestPhasedModel.getModelClass(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
              val projectLoadedModel = resolverContext.getProjectModel(projectModel, projectLoadedModelClass)
              val buildFinishedModel = resolverContext.getProjectModel(projectModel, buildFinishedModelClass)
              Assertions.assertNotNull(projectLoadedModel) {
                "Expected ProjectLoadedModel on the model fetch action completion"
              }
              Assertions.assertNotNull(buildFinishedModel) {
                "Expected BuildFinishedModel on the model fetch action completion"
              }
            }
          }
        }
      }

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()


      projectLoadingAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The model fetch action should be completed only once"
      }
    }
  }

  @Test
  fun `test multi-phased Gradle sync`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val modelFetchPhaseCompletionAssertion = ListenerAssertion()

      val allPhases = GradleModelFetchPhase.entries
      val phasedModelProviders = allPhases.map { TestModelProvider(it) }
      val projectLoadedPhases = allPhases.filter { it <= GradleModelFetchPhase.PROJECT_LOADED_PHASE }
      val completedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      addModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { resolverContext, phase ->
        modelFetchPhaseCompletionAssertion.trace {
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
      whenModelFetchCompleted(disposable) {
        modelFetchCompletionAssertion.trace {
          Assertions.assertEquals(allPhases.toList(), completedPhases.toList()) {
            "All model fetch phases should be completed before the model fetch completion"
          }
        }
      }

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      projectLoadingAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) {
        "The project loaded action should be completed"
      }
      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The model fetch action should be completed"
      }
      modelFetchPhaseCompletionAssertion.assertListenerFailures()
      modelFetchPhaseCompletionAssertion.assertListenerState(allPhases.size) {
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
  fun `test one-phased Gradle sync cancellation by exception`() {
    Disposer.newDisposable().use { disposable ->
      val modelFetchCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      whenModelFetchCompleted(disposable) {
        modelFetchCompletionAssertion.touch()
        throw CancellationException()
      }
      whenExternalSystemTaskStarted(disposable) { _, _ ->
        executionStartAssertion.touch()
      }
      whenExternalSystemTaskFinished(disposable) { _, status ->
        executionFinishAssertion.trace {
          Assertions.assertEquals(OperationExecutionStatus.Cancel, status) {
            "The Gradle sync should be cancelled during execution.\n" +
            "Therefore the Gradle sync should have the cancelled state."
          }
          Assertions.assertDoesNotThrow(ProgressManager::checkCanceled) {
            "Unexpected cancellation in the Gradle sync listeners"
          }
        }
      }

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution.\n" +
        "Therefore the project model contributor shouldn't be runned."
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution."
      }
      executionStartAssertion.assertListenerFailures()
      executionStartAssertion.assertListenerState(1) {
        "Gradle sync should be started."
      }
      executionFinishAssertion.assertListenerFailures()
      executionFinishAssertion.assertListenerState(1) {
        "Gradle sync should be finished."
      }
    }
  }

  @Test
  fun `test two-phased Gradle sync cancellation by exception`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      whenProjectLoaded(disposable) {
        projectLoadingAssertion.touch()
        throw CancellationException()
      }
      whenModelFetchCompleted(disposable) {
        modelFetchCompletionAssertion.touch()
      }
      whenExternalSystemTaskStarted(disposable) { _, _ ->
        executionStartAssertion.touch()
      }
      whenExternalSystemTaskFinished(disposable) { _, status ->
        executionFinishAssertion.trace {
          Assertions.assertEquals(OperationExecutionStatus.Cancel, status) {
            "The Gradle sync should be cancelled during the project loaded action.\n" +
            "Therefore the Gradle sync should have the cancelled state."
          }
          Assertions.assertDoesNotThrow(ProgressManager::checkCanceled) {
            "Unexpected cancellation in the Gradle sync listeners"
          }
        }
      }

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      projectLoadingAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) {
        "The Gradle project loaded action should be completed."
      }
      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(0) {
        "The Gradle sync should be cancelled during the project loaded action.\n" +
        "Therefore the model fetch shouldn't be completed."
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution."
      }
      executionStartAssertion.assertListenerFailures()
      executionStartAssertion.assertListenerState(1) {
        "Gradle sync should be started."
      }
      executionFinishAssertion.assertListenerFailures()
      executionFinishAssertion.assertListenerState(1) {
        "Gradle sync should be finished."
      }
    }
  }

  @Test
  fun `test multi-phased Gradle sync cancellation by exception during project loaded action`() {
    `test multi-phased Gradle sync cancellation by exception`(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
  }

  @Test
  fun `test multi-phased Gradle sync cancellation by exception during build finished action`() {
    `test multi-phased Gradle sync cancellation by exception`(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
  }

  private fun `test multi-phased Gradle sync cancellation by exception`(cancellationPhase: GradleModelFetchPhase) {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val modelFetchPhaseCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      val allPhases = GradleModelFetchPhase.entries
      val phasedModelProviders = allPhases.map { TestModelProvider(it) }
      val expectedCompletedPhases = allPhases.filter { it <= cancellationPhase }
      val actualCompletedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      addModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { _, phase ->
        modelFetchPhaseCompletionAssertion.touch()
        actualCompletedPhases.add(phase)
        if (phase == cancellationPhase) {
          throw CancellationException()
        }
      }
      whenProjectLoaded(disposable) {
        projectLoadingAssertion.touch()
      }
      whenModelFetchCompleted(disposable) {
        modelFetchCompletionAssertion.touch()
      }
      whenExternalSystemTaskStarted(disposable) { _, _ ->
        executionStartAssertion.touch()
      }
      whenExternalSystemTaskFinished(disposable) { _, status ->
        executionFinishAssertion.trace {
          Assertions.assertEquals(OperationExecutionStatus.Cancel, status) {
            "Gradle sync should be cancelled during the $cancellationPhase.\n" +
            "Therefore the Gradle sync should have the cancelled state."
          }
          Assertions.assertDoesNotThrow(ProgressManager::checkCanceled) {
            "Unexpected cancellation in the Gradle sync listeners"
          }
        }
      }

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
          "Actual completed phases = $actualCompletedPhases"
        }
      }
      else {
        projectLoadingAssertion.assertListenerFailures()
        projectLoadingAssertion.assertListenerState(1) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the project loaded action should be completed.\n" +
          "Requested phases = $allPhases\n" +
          "Expected completed phases = $expectedCompletedPhases\n" +
          "Actual completed phases = $actualCompletedPhases"
        }
      }
      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(0) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the model fetch action shouldn't be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $actualCompletedPhases\n" +
        "Expected phases = $expectedCompletedPhases"
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      modelFetchPhaseCompletionAssertion.assertListenerFailures()
      modelFetchPhaseCompletionAssertion.assertListenerState(expectedCompletedPhases.size) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the earliest model fetch phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      Assertions.assertEquals(expectedCompletedPhases, actualCompletedPhases) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      executionStartAssertion.assertListenerFailures()
      executionStartAssertion.assertListenerState(1) {
        "Gradle sync should be started."
      }
      executionFinishAssertion.assertListenerFailures()
      executionFinishAssertion.assertListenerState(1) {
        "Gradle sync should be finished."
      }
    }
  }

  @Test
  fun `test one-phased Gradle sync cancellation by indicator`() {
    Disposer.newDisposable().use { disposable ->
      val modelFetchCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      whenModelFetchCompleted(disposable) { resolverContext ->
        modelFetchCompletionAssertion.trace {
          resolverContext as DefaultProjectResolverContext
          resolverContext.progressIndicator.cancel()
          Assertions.assertTrue(resolverContext.progressIndicator.isCanceled) {
            "The Gradle sync progress indicator should be cancelled after cancellation"
          }
          Assertions.assertTrue(resolverContext.cancellationToken.isCancellationRequested) {
            "The Gradle sync cancellation token should be cancelled after progress indicator cancellation"
          }
          assertCancellation({ delay(10.seconds) }) {
            "The Gradle sync should be cancelled after progress indicator cancellation"
          }
        }
      }
      whenExternalSystemTaskStarted(disposable) { _, _ ->
        executionStartAssertion.touch()
      }
      whenExternalSystemTaskFinished(disposable) { _, status ->
        executionFinishAssertion.trace {
          Assertions.assertEquals(OperationExecutionStatus.Cancel, status) {
            "The Gradle sync should be cancelled during execution.\n" +
            "Therefore the Gradle sync should have the cancelled state."
          }
          Assertions.assertDoesNotThrow(ProgressManager::checkCanceled) {
            "Unexpected cancellation in the Gradle sync listeners"
          }
        }
      }

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(1) {
        "The model fetch action should be completed."
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution."
      }
      executionStartAssertion.assertListenerFailures()
      executionStartAssertion.assertListenerState(1) {
        "Gradle sync should be started."
      }
      executionFinishAssertion.assertListenerFailures()
      executionFinishAssertion.assertListenerState(1) {
        "Gradle sync should be finished."
      }
    }
  }


  @Test
  fun `test two-phased Gradle sync cancellation by indicator`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      whenProjectLoaded(disposable) { resolverContext ->
        projectLoadingAssertion.trace {
          resolverContext as DefaultProjectResolverContext
          resolverContext.progressIndicator.cancel()
          Assertions.assertTrue(resolverContext.progressIndicator.isCanceled) {
            "The Gradle sync progress indicator should be cancelled after cancellation"
          }
          Assertions.assertTrue(resolverContext.cancellationToken.isCancellationRequested) {
            "The Gradle sync cancellation token should be cancelled after progress indicator cancellation"
          }
          assertCancellation({ delay(10.seconds) }) {
            "The Gradle sync should be cancelled after progress indicator cancellation"
          }
        }
      }
      whenModelFetchCompleted(disposable) {
        modelFetchCompletionAssertion.touch()
      }
      whenExternalSystemTaskStarted(disposable) { _, _ ->
        executionStartAssertion.touch()
      }
      whenExternalSystemTaskFinished(disposable) { _, status ->
        executionFinishAssertion.trace {
          Assertions.assertEquals(OperationExecutionStatus.Cancel, status) {
            "The Gradle sync should be cancelled during the project loaded action.\n" +
            "Therefore the Gradle sync should have the cancelled state."
          }
          Assertions.assertDoesNotThrow(ProgressManager::checkCanceled) {
            "Unexpected cancellation in the Gradle sync listeners"
          }
        }
      }

      initMultiModuleProject()
      importProject(errorHandler = { _, _ ->
        syncCancellationAssertion.touch()
      })

      projectLoadingAssertion.assertListenerFailures()
      projectLoadingAssertion.assertListenerState(1) {
        "The Gradle project loaded action should be completed."
      }
      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(0) {
        "The Gradle sync should be cancelled during the project loaded action.\n" +
        "Therefore the model fetch shouldn't be completed."
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "The Gradle sync should be cancelled during execution."
      }
      executionStartAssertion.assertListenerFailures()
      executionStartAssertion.assertListenerState(1) {
        "Gradle sync should be started."
      }
      executionFinishAssertion.assertListenerFailures()
      executionFinishAssertion.assertListenerState(1) {
        "Gradle sync should be finished."
      }
    }
  }

  @Test
  fun `test multi-phased Gradle sync cancellation by indicator during project loaded action`() {
    `test multi-phased Gradle sync cancellation by indicator`(GradleModelFetchPhase.PROJECT_LOADED_PHASE)
  }

  @Test
  fun `test multi-phased Gradle sync cancellation by indicator during build finished action`() {
    `test multi-phased Gradle sync cancellation by indicator`(GradleModelFetchPhase.ADDITIONAL_MODEL_PHASE)
  }

  private fun `test multi-phased Gradle sync cancellation by indicator`(cancellationPhase: GradleModelFetchPhase) {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val modelFetchPhaseCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      val allPhases = GradleModelFetchPhase.entries
      val phasedModelProviders = allPhases.map { TestModelProvider(it) }
      val expectedCompletedPhases = allPhases.filter { it <= cancellationPhase }
      val actualCompletedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      addModelProviders(disposable, phasedModelProviders)
      whenPhaseCompleted(disposable) { resolverContext, phase ->
        modelFetchPhaseCompletionAssertion.trace {
          actualCompletedPhases.add(phase)
          if (phase == cancellationPhase) {
            resolverContext as DefaultProjectResolverContext
            resolverContext.progressIndicator.cancel()
            Assertions.assertTrue(resolverContext.progressIndicator.isCanceled) {
              "The Gradle sync progress indicator should be cancelled after cancellation"
            }
            Assertions.assertTrue(resolverContext.cancellationToken.isCancellationRequested) {
              "The Gradle sync cancellation token should be cancelled after progress indicator cancellation"
            }
            assertCancellation({ delay(10.seconds) }) {
              "The Gradle sync should be cancelled after progress indicator cancellation"
            }
          }
        }
      }
      whenProjectLoaded(disposable) {
        projectLoadingAssertion.touch()
      }
      whenModelFetchCompleted(disposable) {
        modelFetchCompletionAssertion.touch()
      }
      whenExternalSystemTaskStarted(disposable) { _, _ ->
        executionStartAssertion.touch()
      }
      whenExternalSystemTaskFinished(disposable) { _, status ->
        executionFinishAssertion.trace {
          Assertions.assertEquals(OperationExecutionStatus.Cancel, status) {
            "The Gradle sync should be cancelled during the project loaded action.\n" +
            "Therefore the Gradle sync should have the cancelled state.\n" +
            "Requested phases = $allPhases\n" +
            "Expected completed phases = $expectedCompletedPhases\n" +
            "Actual completed phases = $actualCompletedPhases"
          }
          Assertions.assertDoesNotThrow(ProgressManager::checkCanceled) {
            "Unexpected cancellation in the Gradle sync listeners"
          }
        }
      }

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
          "Actual completed phases = $actualCompletedPhases"
        }
      }
      else {
        projectLoadingAssertion.assertListenerFailures()
        projectLoadingAssertion.assertListenerState(1) {
          "Gradle sync should be cancelled during the $cancellationPhase.\n" +
          "Therefore the project loaded action should be completed.\n" +
          "Requested phases = $allPhases\n" +
          "Expected completed phases = $expectedCompletedPhases\n" +
          "Actual completed phases = $actualCompletedPhases"
        }
      }
      modelFetchCompletionAssertion.assertListenerFailures()
      modelFetchCompletionAssertion.assertListenerState(0) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the model fetch action shouldn't be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $actualCompletedPhases\n" +
        "Expected phases = $expectedCompletedPhases"
      }
      syncCancellationAssertion.assertListenerFailures()
      syncCancellationAssertion.assertListenerState(1) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      modelFetchPhaseCompletionAssertion.assertListenerFailures()
      modelFetchPhaseCompletionAssertion.assertListenerState(expectedCompletedPhases.size) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the earliest model fetch phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      Assertions.assertEquals(expectedCompletedPhases, actualCompletedPhases) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      executionStartAssertion.assertListenerFailures()
      executionStartAssertion.assertListenerState(1) {
        "Gradle sync should be started."
      }
      executionFinishAssertion.assertListenerFailures()
      executionFinishAssertion.assertListenerState(1) {
        "Gradle sync should be finished."
      }
    }
  }
}