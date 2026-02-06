// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.google.common.collect.HashBasedTable
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.WorkspaceAssertions
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.delay
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.importing.TestModelProvider
import org.jetbrains.plugins.gradle.importing.TestPhasedModel
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Dynamic.Companion.asSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants
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
        addSyncContributor(phase, disposable) { _, storage -> storage }
        whenSyncPhaseCompleted(phase, disposable) {
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
        val allStaticPhases = allPhases.filterIsInstance<GradleSyncPhase.Static>()
        val allDynamicPhases = allPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
        val completedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

        for (phase in allPhases) {
          addSyncContributor(phase, disposable) { context, storage ->
            val builder = storage.toBuilder()
            syncContributorAssertions.trace {
              val entitySource = GradleTestEntitySource(context.projectPath, phase)
              builder addEntity GradleTestEntity(phase, entitySource)
              Assertions.assertTrue(completedPhases.add(phase)) {
                "The $phase should be completed only once."
              }
            }
            return@addSyncContributor builder.toSnapshot()
          }
          whenSyncPhaseCompleted(phase, disposable) { _ ->
            syncPhaseCompletionAssertions.trace {
              val completedStaticPhases = completedPhases.filterIsInstance<GradleSyncPhase.Static>()
              val completedBaseScriptPhases = completedPhases.filterIsInstance<GradleSyncPhase.BaseScript>()
              val completedDynamicPhases = completedPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
              val expectedEntities = when (phase) {
                is GradleSyncPhase.Static -> when (isSecondarySync) {
                  true -> completedStaticPhases + allDynamicPhases
                  else -> completedStaticPhases
                }
                is GradleSyncPhase.BaseScript -> when (isSecondarySync) {
                  true -> allStaticPhases + completedBaseScriptPhases + allDynamicPhases
                  else -> completedBaseScriptPhases
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
          val builder = storage.toBuilder()
          syncContributorAssertions.trace {
            val entitySource = GradleTestBridgeEntitySource(context.projectPath, phase)
            builder addEntity GradleTestEntity(phase, entitySource)
            Assertions.assertTrue(completedPhases.add(phase)) {
              "The $phase should be completed only once."
            }
          }
          return@addSyncContributor builder.toSnapshot()
        }
        whenSyncPhaseCompleted(phase, disposable) { _ ->
          syncPhaseCompletionAssertions.trace {
            val completedStaticPhases = completedPhases.filterIsInstance<GradleSyncPhase.Static>()
            val completedBaseScriptPhases = completedPhases.filterIsInstance<GradleSyncPhase.BaseScript>()
            val completedDynamicPhases = completedPhases.filterIsInstance<GradleSyncPhase.Dynamic>()
            val expectedEntities = when (phase) {
              is GradleSyncPhase.Static -> completedStaticPhases
              is GradleSyncPhase.BaseScript -> completedBaseScriptPhases
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
        addSyncContributor(phase, disposable) { _, storage -> storage }
        whenSyncPhaseCompleted(phase, disposable) {
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

  @Test
  fun `test dependencies from previous sync are kept`() {
    Disposer.newDisposable().use { disposable ->
      Registry.get("gradle.phased.sync.bridge.disabled").setValue(true, disposable)
      initMultiModuleProject(
        useBuildSrc = false, // buildSrc modules are triggering issue IDEA-383593, and are not essential to this test
      )

      importProject()
      assertMultiModuleProjectStructure(useBuildSrc = false)

      val moduleNames = myProject.modules.map { it.name }

      val dependencyListByModuleNameAtEndOfFirstSync = myProject.workspaceModel.currentSnapshot.entities<ModuleEntity>().associate {
        it.name to it.dependencies
      }

      val dependencyListByModuleNamePerPhase = HashBasedTable.create<GradleSyncPhase, String, List<ModuleDependencyItem>>()
      Disposer.newDisposable().use { disposable ->
        DEFAULT_SYNC_PHASES.forEach { phase ->
          whenSyncPhaseCompleted(phase, disposable) { context ->
            context.project.workspaceModel.currentSnapshot.entities<ModuleEntity>().forEach { moduleEntity ->
              dependencyListByModuleNamePerPhase.put(phase, moduleEntity.name, moduleEntity.dependencies.toList())
            }
          }
        }

        importProject()
        assertMultiModuleProjectStructure(useBuildSrc = false)
      }

      val expectedPhases = DEFAULT_SYNC_PHASES.filterNot {
        setOf( // These phase are not executed in this test case
          GradleSyncPhase.DECLARATIVE_PHASE,
          GradleModelFetchPhase.PROJECT_LOADED_PHASE.asSyncPhase(),
          GradleSyncPhase.DEPENDENCY_MODEL_PHASE
        ).contains(it)
      }

      CollectionAssertions.assertEqualsUnordered(expectedPhases, dependencyListByModuleNamePerPhase.rowKeySet()) {
        """
        Expected phases: $expectedPhases
        Got phases: ${dependencyListByModuleNamePerPhase.rowKeySet()}
        """.trimIndent()
      }

      assertDependencyListPerModulePerPhase(
        moduleNames,
        dependencyListByModuleNameAtEndOfFirstSync,
        expectedPhases,
        dependencyListByModuleNamePerPhase
      )
    }
  }

  @Test
  fun `test dependencies from previous sync are kept - sync contributors can add dependencies and override the sdk explicitly`() {
    Disposer.newDisposable().use { disposable ->
      Registry.get("gradle.phased.sync.bridge.disabled").setValue(true, disposable)
      initMultiModuleProject(
        useBuildSrc = false, // buildSrc modules are triggering issue IDEA-383593, and are not essential to this test
      )

      val modulesToSetSdks = listOf(
        "project.module",
        "includedProject.module"
      )

      val modulesToAddLibraries = listOf(
        "project.main", "project.test",
        "project.module.main", "project.module.test",
        "includedProject.main", "includedProject.test",
        "includedProject.module.main", "includedProject.module.test"
      )

      val (libraryDependency, libraryData) = prepareFakeLibrary()
      val (sdkDependency, sdkData) = prepareFakeSdk()
      val dependencyToAddByModuleName = modulesToAddLibraries.associateWith { libraryDependency } + modulesToSetSdks.associateWith { sdkDependency }
      setupTestDataService(libraryData, sdkData, dependencyToAddByModuleName)
      addDependencySyncContributor(dependencyToAddByModuleName)

      importProject()

      assertMultiModuleProjectStructure(useBuildSrc = false)

      val dependencyListByModuleNameAtEndOfFirstSync = assertDependencyAddedForModules(dependencyToAddByModuleName)
      val dependencyListByModuleNamePerPhase = HashBasedTable.create<GradleSyncPhase, String, List<ModuleDependencyItem>>()

      DEFAULT_SYNC_PHASES.forEach { phase ->
        whenSyncPhaseCompleted(phase, testRootDisposable) { context ->
          context.project.workspaceModel.currentSnapshot.entities<ModuleEntity>().forEach { moduleEntity ->
            dependencyListByModuleNamePerPhase.put(phase, moduleEntity.name, moduleEntity.dependencies.toList())
          }
        }
      }

      importProject()
      assertMultiModuleProjectStructure(useBuildSrc = false)

      val expectedPhases = DEFAULT_SYNC_PHASES.filterNot {
        setOf(
          // These phase are not executed in this test case
          GradleSyncPhase.DECLARATIVE_PHASE,
          GradleModelFetchPhase.PROJECT_LOADED_PHASE.asSyncPhase(),
        ).contains(it)
      }

      CollectionAssertions.assertEqualsUnordered(expectedPhases, dependencyListByModuleNamePerPhase.rowKeySet()) {
        """
        Expected phases: $expectedPhases
        Got phases: ${dependencyListByModuleNamePerPhase.rowKeySet()}
        """.trimIndent()
      }

      assertDependencyListPerModulePerPhase(
        dependencyToAddByModuleName.keys,
        dependencyListByModuleNameAtEndOfFirstSync,
        expectedPhases,
        dependencyListByModuleNamePerPhase
      )
    }
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
      val syncContributorAssertion = ListenerAssertion()
      val syncListenerAssertion = ListenerAssertion()
      val syncCancellationAssertion = ListenerAssertion()
      val executionStartAssertion = ListenerAssertion()
      val executionFinishAssertion = ListenerAssertion()

      val allPhases = DEFAULT_SYNC_PHASES
      val expectedCompletedPhases = allPhases.filter { it < cancellationPhase }
      val actualCompletedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

      addProjectResolverExtension(TestProjectResolverExtension::class.java, disposable) {
        addModelProviders(DEFAULT_MODEL_FETCH_PHASES.map(::TestModelProvider))
      }
      for (phase in allPhases) {
        addSyncContributor(phase, disposable) { context, storage ->
          syncContributorAssertion.trace {
            if (phase == cancellationPhase) {
              assertCancellation({ cancellation(context) }) {
                "The Gradle sync should be cancelled after cancellation"
              }
            }
          }
          return@addSyncContributor storage
        }
        whenSyncPhaseCompleted(phase, disposable) {
          syncListenerAssertion.trace {
            actualCompletedPhases.add(phase)
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
      syncContributorAssertion.assertListenerFailures()
      syncContributorAssertion.assertListenerState(expectedCompletedPhases.size + 1) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the earlier sync phases should be completed.\n" +
        "Requested phases = $allPhases\n" +
        "Expected completed phases = $expectedCompletedPhases\n" +
        "Actual completed phases = $actualCompletedPhases"
      }
      syncListenerAssertion.assertListenerFailures()
      syncListenerAssertion.assertListenerState(expectedCompletedPhases.size) {
        "Gradle sync should be cancelled during the $cancellationPhase.\n" +
        "Therefore the earlier sync phases should be completed.\n" +
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

  private fun assertDependencyAddedForModules(dependencyToAddByModuleName: Map<String, ModuleDependencyItem>): Map<@NlsSafe String, List<ModuleDependencyItem>> {
    val dependencyListByModuleName = myProject.workspaceModel.currentSnapshot
      .entities<ModuleEntity>()
      .filter { it.name in dependencyToAddByModuleName.keys }
      .associate {
        val expected = dependencyToAddByModuleName[it.name]!!
        assertTrue("""
              Expected to contain the added dependency for module ${it.name}: 
                $expected
                but is: 
                ${it.dependencies}
            """.trimIndent(), it.dependencies.contains(expected))
        it.name to it.dependencies
      }

    CollectionAssertions.assertContainsUnordered(
      dependencyToAddByModuleName.keys,
      dependencyListByModuleName.keys
    )
    return dependencyListByModuleName
  }

  private fun assertDependencyListPerModulePerPhase(
    moduleNames: Collection<String>,
    dependencyListByModuleNameAtEndOfFirstSync: Map<@NlsSafe String, List<ModuleDependencyItem>>,
    expectedPhases: List<GradleSyncPhase>,
    dependencyListByModuleNamePerPhase: HashBasedTable<GradleSyncPhase, String, List<ModuleDependencyItem>>,
  ) {
    moduleNames.forEach { moduleName ->
      val originalDependencies = dependencyListByModuleNameAtEndOfFirstSync[moduleName]
      assertNotNull("Expected dependency list of $moduleName to be initialized!", originalDependencies)
      expectedPhases.forEach { phase ->
        val dependenciesAtEndOfPhase = dependencyListByModuleNamePerPhase.get(phase, moduleName)
        assertNotNull("Expected to find dependencies of $moduleName at the end of phase $phase", dependenciesAtEndOfPhase)
        CollectionAssertions.assertEqualsUnordered(originalDependencies, dependenciesAtEndOfPhase) {
          "Dependency list doesn't match for module: $moduleName"
        }
      }
    }
  }


  private fun addDependencySyncContributor(
    dependencyToAddByModuleName: Map<String, ModuleDependencyItem>,
  ) {
    addSyncContributor(GradleSyncPhase.DEPENDENCY_MODEL_PHASE, testRootDisposable) { context, storage ->
      val mutableStorage = storage.toBuilder()
      mutableStorage.entities<ModuleEntity>()
        .forEach { entity ->
          dependencyToAddByModuleName[entity.name]?.let {
            mutableStorage.modifyModuleEntity(entity) {
              this.dependencies = mutableListOf(it)
            }
          }
        }
      mutableStorage.toSnapshot()
    }
  }

  private fun setupTestDataService(
    libraryData: LibraryData,
    sdkData: ModuleSdkData,
    dependencyToAddByModuleName: Map<String, ModuleDependencyItem>,
  ) {
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(TestDependencyResolverExtension(), testRootDisposable)
    val service = TestDependencyProjectResolverService(libraryData, sdkData, dependencyToAddByModuleName)
    myProject.registerServiceInstance(TestDependencyProjectResolverService::class.java, service)
  }

  private fun prepareFakeSdk(): Pair<SdkDependency, ModuleSdkData> {
    val type = JavaSdk.getInstance()
    val sdkDependency = SdkDependency(SdkId("sdk-name", type.name))
    val jdk = ProjectJdkTable.getInstance().createSdk(sdkDependency.sdk.name, type)
    WriteAction.runAndWait<Throwable> {
      ProjectJdkTable.getInstance().addJdk(jdk, testRootDisposable)
    }
    val sdkData = ModuleSdkData(sdkDependency.sdk.name)
    return Pair(sdkDependency, sdkData)
  }

  private fun prepareFakeLibrary(): Pair<LibraryDependency, LibraryData> {
    val libraryName = "some-library"
    val libraryDependency = LibraryDependency(
      LibraryId("Gradle: $libraryName", LibraryTableId.ProjectLibraryTableId),
      exported = false,
      DependencyScope.COMPILE
    )
    val libraryData = LibraryData(GradleConstants.SYSTEM_ID, libraryName)
    return Pair(libraryDependency, libraryData)
  }

  /** Need to use a project level service for any data use by the [TestDependencyResolverExtension] as the instance gets recreated. */
  class TestDependencyProjectResolverService(
    // This extension only supports adding a singular instance of a library data and an Sdk data for simplicity
    val libraryData: LibraryData,
    val sdkData: ModuleSdkData,
    // Whether to use sdk or library data is determined by the type of the ModuleDependencyItem
    val dependencyToAddByModuleName: Map<String, ModuleDependencyItem>,
  ): AbstractTestProjectResolverService()

  /**
   * A test resolver extension to populate fake library and sdk dependencies for modules according
   * to the data provided by [TestDependencyProjectResolverService]
   */
  class TestDependencyResolverExtension: AbstractProjectResolverExtension() {
    val service get() = resolverCtx.externalSystemTaskId.findProject()!!.service<TestDependencyProjectResolverService>()
    val libraryData: LibraryData get() = service.libraryData
    val sdkData: ModuleSdkData get() = service.sdkData
    val dependencyToAddByModuleName: Map<String, ModuleDependencyItem> get() = service.dependencyToAddByModuleName

    override fun populateProjectExtraModels(
      gradleProject: IdeaProject,
      ideProject: DataNode<ProjectData?>,
    ) {
      ideProject.createChild(ProjectKeys.LIBRARY, libraryData);
      super.populateProjectExtraModels(gradleProject, ideProject)
    }

    override fun populateModuleDependencies(
      gradleModule: IdeaModule,
      ideModule: DataNode<ModuleData>,
      ideProject: DataNode<ProjectData>,
    ) {
      (ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY) + ideModule).forEach {
        val dependencyToAdd = dependencyToAddByModuleName[it.data.internalName] ?: return@forEach
        if (dependencyToAdd !is LibraryDependency) return@forEach
        it.createChild(
          ProjectKeys.LIBRARY_DEPENDENCY,
          LibraryDependencyData(
            it.data,
            libraryData,
            LibraryLevel.PROJECT
          )
        )
      }
      super.populateModuleDependencies(gradleModule, ideModule, ideProject)
    }

    override fun populateModuleExtraModels(
      gradleModule: IdeaModule,
      ideModule: DataNode<ModuleData?>,
    ) {
      (ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY) + ideModule).forEach {
        val dependencyToAdd = dependencyToAddByModuleName[it.data.internalName] ?: return@forEach
        if (dependencyToAdd !is SdkDependency) return@forEach
        checkNotNull(ExternalSystemApiUtil.find(it, ModuleSdkData.KEY)) {
          "Expected to find existing SDK data node for ${it.data.internalName}"
        }.clear(true)
        it.createChild(
          ModuleSdkData.KEY,
          sdkData
        )
      }
      super.populateModuleExtraModels(gradleModule, ideModule)
    }
  }
}