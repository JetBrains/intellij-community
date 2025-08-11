// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.testFramework.assertion.WorkspaceAssertions
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import kotlinx.coroutines.delay
import org.jetbrains.plugins.gradle.importing.TestModelProvider
import org.jetbrains.plugins.gradle.importing.TestPhasedModel
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic.Companion.asSyncPhase
import org.jetbrains.plugins.gradle.util.entity.GradleTestBridgeEntitySource
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntity
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntityId
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntitySource
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskFinished
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskStarted
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class GradlePhasedSyncTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test Gradle model fetch phase completion`() {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val modelFetchPhaseCompletionAssertion = ListenerAssertion()

      val allPhases = DEFAULT_MODEL_FETCH_PHASES
      val completedPhases = CopyOnWriteArrayList<GradleModelFetchPhase>()

      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(DEFAULT_MODEL_FETCH_PHASES.map(::TestModelProvider))
      }
      whenModelFetchPhaseCompleted(disposable) { resolverContext, phase ->
        modelFetchPhaseCompletionAssertion.trace {
          for (completedPhase in completedPhases) {
            Assertions.assertTrue(completedPhase < phase) {
              "The $phase should be completed before the $completedPhase.\n" +
              "Requested phases = $allPhases\n" +
              "Completed phases = $completedPhases"
            }
          }
          Assertions.assertTrue(completedPhases.add(phase)) {
            "The $phase should be completed only once.\n" +
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
          val projectLoadedPhases = allPhases.filterIsInstance<GradleModelFetchPhase.ProjectLoaded>()
          Assertions.assertEquals(projectLoadedPhases, completedPhases.toList()) {
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
        "All requested model fetch phases should be handled.\n" +
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
  fun `test Gradle sync phase completion`() {
    Disposer.newDisposable().use { disposable ->
      val syncContributorAssertions = ListenerAssertion()

      val allPhases = DEFAULT_SYNC_PHASES
      val completedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(DEFAULT_MODEL_FETCH_PHASES.map(::TestModelProvider))
      }
      for (phase in allPhases) {
        addSyncContributor(phase, disposable) { _, _ ->
          syncContributorAssertions.trace {
            for (completedPhase in completedPhases) {
              Assertions.assertTrue(completedPhase < phase) {
                "The $phase should be completed before the $completedPhase.\n" +
                "Requested phases = $allPhases\n" +
                "Completed phases = $completedPhases"
              }
            }
            Assertions.assertTrue(completedPhases.add(phase)) {
              "The $phase should be completed only once.\n" +
              "Requested phases = $allPhases\n" +
              "Completed phases = $completedPhases"
            }
          }
        }
      }

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      syncContributorAssertions.assertListenerFailures()
      syncContributorAssertions.assertListenerState(allPhases.size) {
        "All requested sync phases should be handled.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
      Assertions.assertEquals(allPhases, completedPhases) {
        "All requested sync phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Completed phases = $completedPhases"
      }
    }
  }

  @Test
  fun `test entity contribution on Gradle sync phase`() {
    repeat(2) { index ->
      Disposer.newDisposable().use { disposable ->

        val isSecondarySync = index == 1

        val syncContributorAssertions = ListenerAssertion()
        val syncPhaseCompletionAssertions = ListenerAssertion()

        val allPhases = DEFAULT_SYNC_PHASES
        val allDynamicPhases = allPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
        val completedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

        for (phase in allPhases) {
          addSyncContributor(phase, disposable) { context, storage ->
            syncContributorAssertions.trace {
              val entitySource = GradleTestEntitySource(context.projectPath, phase)
              storage addEntity GradleTestEntity(phase, entitySource)
              Assertions.assertTrue(completedPhases.add(phase)) {
                "The $phase should be completed only once."
              }
            }
          }
          whenSyncPhaseCompleted(phase, disposable) { _ ->
            syncPhaseCompletionAssertions.trace {
              val completedStaticPhases = completedPhases.filterIsInstance<GradleSyncPhase.Static>()
              val completedDynamicPhases = completedPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
              val expectedEntities = when (phase) {
                is GradleSyncPhase.Static -> when (isSecondarySync) {
                  true -> completedStaticPhases + allDynamicPhases
                  else -> completedStaticPhases
                }
                is GradleSyncPhase.Dynamic -> when (isSecondarySync) {
                  true -> allDynamicPhases
                  else -> completedDynamicPhases
                }
              }
              WorkspaceAssertions.assertEntities(myProject, expectedEntities.map { GradleTestEntityId(it) }) {
                "Entities should be created for completed phases.\n" +
                "Completed phases = $completedPhases\n"
                "isSecondarySync = $isSecondarySync"
              }
            }
          }
        }

        initMultiModuleProject()
        importProject()
        assertMultiModuleProjectStructure()

        syncContributorAssertions.assertListenerFailures()
        syncContributorAssertions.assertListenerState(allPhases.size) {
          "All requested sync phases should be handled."
        }
        syncPhaseCompletionAssertions.assertListenerFailures()
        syncPhaseCompletionAssertions.assertListenerState(allPhases.size) {
          "All requested sync phases should be completed."
        }

        WorkspaceAssertions.assertEntities(myProject, allDynamicPhases.map { GradleTestEntityId(it) }) {
          "Entities should be created for completed phases.\n" +
          "Requested phases = $allPhases"
          "Completed phases = $completedPhases"
        }
      }
    }
  }

  @Test
  fun `test bridge entity contribution on Gradle sync phase`() {
    Disposer.newDisposable().use { disposable ->

      val syncContributorAssertions = ListenerAssertion()
      val syncPhaseCompletionAssertions = ListenerAssertion()

      val allPhases = DEFAULT_SYNC_PHASES
      val completedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

      for (phase in allPhases) {
        addSyncContributor(phase, disposable) { context, storage ->
          syncContributorAssertions.trace {
            val entitySource = GradleTestBridgeEntitySource(context.projectPath, phase)
            storage addEntity GradleTestEntity(phase, entitySource)
            Assertions.assertTrue(completedPhases.add(phase)) {
              "The $phase should be completed only once."
            }
          }
        }
        whenSyncPhaseCompleted(phase, disposable) { _ ->
          syncPhaseCompletionAssertions.trace {
            val completedStaticPhases = completedPhases.filterIsInstance<GradleSyncPhase.Static>()
            val completedDynamicPhases = completedPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
            val expectedEntities = when (phase) {
              is GradleSyncPhase.Static -> completedStaticPhases
              is GradleSyncPhase.Dynamic -> completedDynamicPhases
            }
            WorkspaceAssertions.assertEntities(myProject, expectedEntities.map { GradleTestEntityId(it) }) {
              "Bridge entities should be created for completed phases.\n" +
              "Completed phases = $completedPhases"
            }
          }
        }
      }

      initMultiModuleProject()
      importProject()
      assertMultiModuleProjectStructure()

      syncContributorAssertions.assertListenerFailures()
      syncContributorAssertions.assertListenerState(allPhases.size) {
        "All requested sync phases should be handled."
      }
      syncPhaseCompletionAssertions.assertListenerFailures()
      syncPhaseCompletionAssertions.assertListenerState(allPhases.size) {
        "All requested sync phases should be completed."
      }

      WorkspaceAssertions.assertEntities(myProject, emptyList<GradleTestEntityId>()) {
        "All bridge entities should be removed when sync is completed.\n" +
        "Requested phases = $allPhases"
        "Completed phases = $completedPhases"
      }
    }
  }

  @Test
  fun `test phased Gradle sync for custom static phase without model provider`() {
    `test phased Gradle sync for custom phase without model provider`(
      GradleSyncPhase.Static(10_000, "CUSTOM_PHASE")
    )
  }

  @Test
  fun `test phased Gradle sync for custom project loaded phase without model provider`() {
    `test phased Gradle sync for custom phase without model provider`(
      GradleModelFetchPhase.ProjectLoaded(10_000, "CUSTOM_PHASE").asSyncPhase()
    )
  }

  @Test
  fun `test phased Gradle sync for custom build finished phase without model provider`() {
    `test phased Gradle sync for custom phase without model provider`(
      GradleModelFetchPhase.BuildFinished(10_000, "CUSTOM_PHASE").asSyncPhase()
    )
  }

  fun `test phased Gradle sync for custom phase without model provider`(customPhase: GradleSyncPhase) {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val modelFetchPhaseCompletionAssertion = ListenerAssertion()

      val allPhases = (DEFAULT_SYNC_PHASES + customPhase).sorted()
      val completedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

      for (phase in allPhases) {
        addSyncContributor(phase, disposable) { _, _ ->
          modelFetchPhaseCompletionAssertion.trace {
            completedPhases.add(phase)
          }
        }
      }
      whenProjectLoaded(disposable) {
        projectLoadingAssertion.trace {
          val staticPhases = allPhases.filterIsInstance<GradleSyncPhase.Static>()
          val projectLoadedPhases = allPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
            .filter { it.modelFetchPhase is GradleModelFetchPhase.ProjectLoaded }
          Assertions.assertEquals(staticPhases + projectLoadedPhases, completedPhases.toList()) {
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
  fun `test phased Gradle sync cancellation by exception during initialisation`() {
    `test phased Gradle sync cancellation by exception`(GradleSyncPhase.INITIAL_PHASE)
  }

  @Test
  fun `test phased Gradle sync cancellation by exception during project loaded action`() {
    `test phased Gradle sync cancellation by exception`(GradleModelFetchPhase.PROJECT_LOADED_PHASE.asSyncPhase())
  }

  @Test
  fun `test phased Gradle sync cancellation by exception during build finished action`() {
    `test phased Gradle sync cancellation by exception`(GradleSyncPhase.ADDITIONAL_MODEL_PHASE)
  }

  private fun `test phased Gradle sync cancellation by exception`(cancellationPhase: GradleSyncPhase) {
    `test phased Gradle sync cancellation`(cancellationPhase) {
      throw CancellationException()
    }
  }

  @Test
  fun `test phased Gradle sync cancellation by indicator during initialisation`() {
    `test phased Gradle sync cancellation by indicator`(GradleSyncPhase.INITIAL_PHASE)
  }

  @Test
  fun `test phased Gradle sync cancellation by indicator during project loaded action`() {
    `test phased Gradle sync cancellation by indicator`(GradleModelFetchPhase.PROJECT_LOADED_PHASE.asSyncPhase())
  }

  @Test
  fun `test phased Gradle sync cancellation by indicator during build finished action`() {
    `test phased Gradle sync cancellation by indicator`(GradleSyncPhase.ADDITIONAL_MODEL_PHASE)
  }

  private fun `test phased Gradle sync cancellation by indicator`(cancellationPhase: GradleSyncPhase) {
    `test phased Gradle sync cancellation`(cancellationPhase) { resolverContext ->
      resolverContext as DefaultProjectResolverContext
      resolverContext.progressIndicator.cancel()
      Assertions.assertTrue(resolverContext.progressIndicator.isCanceled) {
        "The Gradle sync progress indicator should be cancelled after cancellation"
      }
      Assertions.assertTrue(resolverContext.cancellationToken.isCancellationRequested) {
        "The Gradle sync cancellation token should be cancelled after progress indicator cancellation"
      }
      /**
       * Coroutine Job polls progress indicator state
       * Therefore, progress indicator state propagates with small delay
       * @see com.intellij.openapi.progress.cancelWithIndicator
       */
      delay(10.seconds)
    }
  }

  private fun `test phased Gradle sync cancellation`(
    cancellationPhase: GradleSyncPhase,
    cancellation: suspend (ProjectResolverContext) -> Unit,
  ) {
    Disposer.newDisposable().use { disposable ->
      val projectLoadingAssertion = ListenerAssertion()
      val modelFetchCompletionAssertion = ListenerAssertion()
      val modelFetchPhaseCompletionAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      val allPhases = DEFAULT_SYNC_PHASES
      val expectedCompletedPhases = allPhases.filter { it <= cancellationPhase }
      val actualCompletedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(DEFAULT_MODEL_FETCH_PHASES.map(::TestModelProvider))
      }
      for (phase in allPhases) {
        addSyncContributor(phase, disposable) { resolverContext, _ ->
          modelFetchPhaseCompletionAssertion.trace {
            actualCompletedPhases.add(phase)
            if (phase == cancellationPhase) {
              assertCancellation({ cancellation(resolverContext) }) {
                "The Gradle sync should be cancelled after cancellation"
              }
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
            "Gradle sync should be cancelled during the $cancellationPhase.\n" +
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

      if (cancellationPhase <= GradleModelFetchPhase.PROJECT_LOADED_PHASE.asSyncPhase()) {
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