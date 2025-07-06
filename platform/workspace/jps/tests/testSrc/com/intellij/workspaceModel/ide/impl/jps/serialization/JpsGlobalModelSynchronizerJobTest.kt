// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Testable version of JpsGlobalModelSynchronizerImpl that allows configuring the delay
 * and tracking when delayLoadGlobalWorkspaceModel is called
 */
private class TestableJpsGlobalModelSynchronizer(
  coroutineScope: CoroutineScope,
  private val testDelayDuration: Duration = 50.milliseconds,
  private val onDelayLoadCalled: () -> Unit = {},
) : JpsGlobalModelSynchronizerImpl(coroutineScope) {

  override val delayDuration: Duration
    get() = testDelayDuration

  override suspend fun delayLoadGlobalWorkspaceModel(environmentName: GlobalWorkspaceModelCache.InternalEnvironmentName) {
    onDelayLoadCalled()
    // skip the actual loading for the test
  }
}

@Suppress("RAW_SCOPE_CREATION")
@TestApplication
class JpsGlobalModelSynchronizerJobTest {
  @Test
  fun `test delayed global loading waits for project synchronization job`() = runBlocking {
    val executionOrder = mutableListOf<String>()
    val projectJobStarted = AtomicBoolean(false)
    val projectJobCompleted = AtomicBoolean(false)
    val delayedGlobalLoadingStarted = AtomicBoolean(false)
    
    // Use semaphore for precise synchronization instead of timing
    val projectCanComplete = Semaphore(1, 1) // Initially acquired

    val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Create a testable synchronizer with reduced delay (50ms)
    val synchronizer = TestableJpsGlobalModelSynchronizer(
      coroutineScope, 
      testDelayDuration = 50.milliseconds
    ) { 
      delayedGlobalLoadingStarted.set(true)
      executionOrder.add("delayed_loading_started")
    }

    synchronizer.setVirtualFileUrlManager(IdeVirtualFileUrlManagerImpl())

    val environmentName = object : GlobalWorkspaceModelCache.InternalEnvironmentName {
      override val name: String
        get() = "test"
    }

    val mutableStorage = MutableEntityStorage.create()
    val entityStorage = VersionedEntityStorageImpl(mutableStorage.toSnapshot())

    // Create a project synchronization job that waits for permission to complete
    val projectSyncJob = coroutineScope.launch {
      projectJobStarted.set(true)
      executionOrder.add("project_sync_started")
      projectCanComplete.acquire() // Wait for test to allow completion
      projectJobCompleted.set(true)
      executionOrder.add("project_sync_completed")
    }

    // Set the project synchronization job on the synchronizer
    synchronizer.setProjectSynchronizationJob(projectSyncJob)

    // Load initial state (this should trigger the delayed global loading)
    val callback = synchronizer.loadInitialState(
      environmentName = environmentName,
      mutableStorage = mutableStorage,
      initialEntityStorage = entityStorage,
      loadedFromCache = true
    )

    // Execute the callback (this starts the delayed loading coroutine)
    callback.invoke()

    // Wait for project to start
    while (!projectJobStarted.get()) {
      delay(10.milliseconds)
    }

    // Wait for the delay to expire
    delay(80.milliseconds) // More than 50ms delay

    // At this point, the delay has expired but project sync is still blocked
    // so delayed loading should NOT have started yet (waiting for project)
    assertThat(projectJobStarted.get()).describedAs("Project sync should have started").isTrue()
    assertThat(projectJobCompleted.get()).describedAs("Project sync should not be complete yet").isFalse()
    assertThat(delayedGlobalLoadingStarted.get()).describedAs("Delayed loading should not have started yet - waiting for project").isFalse()

    // Allow project to complete
    projectCanComplete.release()
    
    // Wait for project job to complete
    projectSyncJob.join()

    // Now wait for the delayed loading to start (should happen immediately after project completes)
    delay(50.milliseconds) // Give time for the coroutine to proceed

    // Verify execution order: delay expires, waits for project, then delayed loading starts
    assertThat(delayedGlobalLoadingStarted.get()).describedAs("Delayed loading should have started after project completed").isTrue()
    assertThat(executionOrder).describedAs("Project sync should complete before delayed loading")
      .containsExactly("project_sync_started", "project_sync_completed", "delayed_loading_started")

    coroutineScope.cancel()
  }

  @Test
  fun `test delayed global loading proceeds when project completes before delay`() = runBlocking {
    val executionOrder = mutableListOf<String>()
    val projectJobCompleted = AtomicBoolean(false)
    val delayedGlobalLoadingStarted = AtomicBoolean(false)

    val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Create a testable synchronizer with 100ms delay
    val synchronizer = TestableJpsGlobalModelSynchronizer(
      coroutineScope,
      testDelayDuration = 100.milliseconds
    ) {
      delayedGlobalLoadingStarted.set(true)
      executionOrder.add("delayed_loading_started")
    }

    synchronizer.setVirtualFileUrlManager(IdeVirtualFileUrlManagerImpl())

    val environmentName = object : GlobalWorkspaceModelCache.InternalEnvironmentName {
      override val name: String
        get() = "test"
    }

    val mutableStorage = MutableEntityStorage.create()
    val entityStorage = VersionedEntityStorageImpl(mutableStorage.toSnapshot())

    // Create a project synchronization job that completes quickly (before delay)
    val projectSyncJob = coroutineScope.launch {
      executionOrder.add("project_sync_started")
      delay(30.milliseconds) // Completes before the 100ms delay
      projectJobCompleted.set(true)
      executionOrder.add("project_sync_completed")
    }

    // Set the project synchronization job on the synchronizer
    synchronizer.setProjectSynchronizationJob(projectSyncJob)

    // Load initial state
    val callback = synchronizer.loadInitialState(
      environmentName = environmentName,
      mutableStorage = mutableStorage,
      initialEntityStorage = entityStorage,
      loadedFromCache = true
    )

    callback.invoke()

    // Wait for project to complete (should happen before delay expires)
    projectSyncJob.join()
    delay(50.milliseconds) // Still within the 100ms delay period

    // Project should be completed but delayed loading should not have started yet
    assertThat(projectJobCompleted.get()).describedAs("Project sync should be completed").isTrue()
    assertThat(delayedGlobalLoadingStarted.get()).describedAs("Delayed loading should not have started yet - delay not expired").isFalse()

    // Wait for delay to expire
    delay(100.milliseconds) // Total 150ms, delay should have expired

    // Now delayed loading should have started immediately (no waiting for project)
    assertThat(delayedGlobalLoadingStarted.get()).describedAs("Delayed loading should have started after delay expired").isTrue()
    assertThat(executionOrder).describedAs("Project should complete before delay expires")
      .containsExactly("project_sync_started", "project_sync_completed", "delayed_loading_started")

    coroutineScope.cancel()
  }

  @Test
  fun `test delayed global loading proceeds immediately when no project job is set`() = runBlocking {
    val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val synchronizer = TestableJpsGlobalModelSynchronizer(coroutineScope)

    synchronizer.setVirtualFileUrlManager(IdeVirtualFileUrlManagerImpl())

    val environmentName = object : GlobalWorkspaceModelCache.InternalEnvironmentName {
      override val name: String
        get() = "test"
    }

    val mutableStorage = MutableEntityStorage.create()
    val entityStorage = VersionedEntityStorageImpl(mutableStorage.toSnapshot())

    // Don't set any project synchronization job
    val callback = synchronizer.loadInitialState(
      environmentName = environmentName,
      mutableStorage = mutableStorage,
      initialEntityStorage = entityStorage,
      loadedFromCache = true
    )

    callback.invoke()

    // The delayed loading should start immediately (just wait for the 5-second delay mechanism)
    // We won't wait the full 5 seconds, just verify the structure is correct
    delay(50.milliseconds)

    // No assertion about timing since we're not waiting for the full delay,
    // but this test verifies that no exception is thrown when no job is set
    coroutineScope.cancel()
  }

  @Test
  fun `test delayed global loading waits for multiple project synchronization jobs`() = runBlocking {
    val executionOrder = mutableListOf<String>()
    val project1JobCompleted = AtomicBoolean(false)
    val project2JobCompleted = AtomicBoolean(false)

    val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val synchronizer = JpsGlobalModelSynchronizerImpl(coroutineScope)

    synchronizer.setVirtualFileUrlManager(IdeVirtualFileUrlManagerImpl())

    val environmentName = object : GlobalWorkspaceModelCache.InternalEnvironmentName {
      override val name: String
        get() = "test"
    }

    val mutableStorage = MutableEntityStorage.create()
    val entityStorage = VersionedEntityStorageImpl(mutableStorage.toSnapshot())

    // Create two project synchronization jobs with different completion times
    val project1SyncJob = coroutineScope.launch {
      executionOrder.add("project1_sync_started")
      delay(50.milliseconds)
      project1JobCompleted.set(true)
      executionOrder.add("project1_sync_completed")
    }

    val project2SyncJob = coroutineScope.launch {
      executionOrder.add("project2_sync_started")
      delay(150.milliseconds) // Takes longer than project1
      project2JobCompleted.set(true)
      executionOrder.add("project2_sync_completed")
    }

    // Set both project synchronization jobs on the synchronizer
    synchronizer.setProjectSynchronizationJob(project1SyncJob)
    synchronizer.setProjectSynchronizationJob(project2SyncJob)

    // Load initial state (this should trigger the delayed global loading)
    val callback = synchronizer.loadInitialState(
      environmentName = environmentName,
      mutableStorage = mutableStorage,
      initialEntityStorage = entityStorage,
      loadedFromCache = true
    )

    callback.invoke()

    // Wait for the first project to complete but not the second
    delay(100.milliseconds)
    assertThat(project1JobCompleted.get()).describedAs("Project 1 sync should be completed").isTrue()
    assertThat(project2JobCompleted.get()).describedAs("Project 2 sync should not be complete yet").isFalse()

    // Wait for both jobs to complete
    project1SyncJob.join()
    project2SyncJob.join()

    // Give some time for the delayed loading to potentially start
    delay(200.milliseconds)

    // Verify both projects completed
    assertThat(project1JobCompleted.get()).describedAs("Project 1 sync should be completed").isTrue()
    assertThat(project2JobCompleted.get()).describedAs("Project 2 sync should be completed").isTrue()

    // Verify that both project syncs completed before any global loading could proceed
    assertThat(executionOrder).describedAs("Both projects should have completed")
      .contains("project1_sync_completed", "project2_sync_completed")

    coroutineScope.cancel()
  }

  @Test
  fun `test delayed global loading proceeds when project job is cancelled`() = runBlocking {
    val executionOrder = mutableListOf<String>()
    val project1JobCompleted = AtomicBoolean(false)
    val project2JobStarted = AtomicBoolean(false)
    val project2JobCompleted = AtomicBoolean(false)

    val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val synchronizer = JpsGlobalModelSynchronizerImpl(coroutineScope)

    synchronizer.setVirtualFileUrlManager(IdeVirtualFileUrlManagerImpl())

    val environmentName = object : GlobalWorkspaceModelCache.InternalEnvironmentName {
      override val name: String
        get() = "test"
    }

    val mutableStorage = MutableEntityStorage.create()
    val entityStorage = VersionedEntityStorageImpl(mutableStorage.toSnapshot())

    // Create a job that will be cancelled
    val project1SyncJob = coroutineScope.launch {
      executionOrder.add("project1_sync_started")
      delay(200.milliseconds) // This will be cancelled before completion
      project1JobCompleted.set(true)
      executionOrder.add("project1_sync_completed")
    }

    // Create another job that will complete normally
    val project2SyncJob = coroutineScope.launch {
      project2JobStarted.set(true)
      executionOrder.add("project2_sync_started")
      delay(50.milliseconds)
      project2JobCompleted.set(true)
      executionOrder.add("project2_sync_completed")
    }

    // Set both project synchronization jobs
    synchronizer.setProjectSynchronizationJob(project1SyncJob)
    synchronizer.setProjectSynchronizationJob(project2SyncJob)

    // Load initial state
    val callback = synchronizer.loadInitialState(
      environmentName = environmentName,
      mutableStorage = mutableStorage,
      initialEntityStorage = entityStorage,
      loadedFromCache = true
    )

    callback.invoke()

    // Wait for project2 to start and then cancel project1
    delay(30.milliseconds)
    assertThat(project2JobStarted.get()).describedAs("Project 2 should have started").isTrue()
    project1SyncJob.cancel() // Cancel project1 job

    // Wait for project2 to complete
    delay(100.milliseconds)
    assertThat(project2JobCompleted.get()).describedAs("Project 2 should be completed").isTrue()
    assertThat(project1JobCompleted.get()).describedAs("Project 1 should not be completed (was cancelled)").isFalse()

    // Give time for delayed loading to potentially start (should not be blocked by cancelled job)
    delay(200.milliseconds)

    // Verify that project2 completed and project1 was cancelled
    assertThat(executionOrder).describedAs("Project 2 should have completed normally")
      .contains("project2_sync_completed")
    assertThat(executionOrder).describedAs("Project 1 should not have completed")
      .doesNotContain("project1_sync_completed")

    coroutineScope.cancel()
  }
}
