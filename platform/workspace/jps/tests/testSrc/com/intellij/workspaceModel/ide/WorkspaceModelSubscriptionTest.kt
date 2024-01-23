// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.writeAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.NamedEntity
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WorkspaceModelSubscriptionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test(timeout = 1_000)
  fun `check subscribe`() = runBlocking {
    val firstStorageChannel = Channel<ImmutableEntityStorage>(1)
    val firstChange = Channel<VersionedStorageChange>(1)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val job = launch {
      workspaceModel.subscribe { starting, flow ->
        firstStorageChannel.send(starting)
        firstChange.send(flow.first())
      }
    }

    val firstStorage = firstStorageChannel.receive()
    assertTrue(firstStorage.entities<ModuleEntity>().toList().isEmpty())

    writeAction {
      workspaceModel.updateProjectModel("Test add new module asynchronously") {
        it addEntity ModuleEntity("MyModule", emptyList(), object : EntitySource {})
      }
    }

    val change = firstChange.receive()
    assertEquals(1, change.storageAfter.entities<ModuleEntity>().toList().size)

    job.join()
  }

  @Test(timeout = 10_000)
  fun `check first five updates are sequential`() = runBlocking {
    val firstStorageChannel = Channel<ImmutableEntityStorage>(1)
    val channelUpdates = Channel<VersionedStorageChange>(1)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val job = launch {
      workspaceModel.subscribe { starting, flow ->
        firstStorageChannel.send(starting)
        flow.take(5).collect { channelUpdates.send(it) }
      }
    }

    val firstStorage = firstStorageChannel.receive()
    assertTrue(firstStorage.entities<ModuleEntity>().toList().isEmpty())

    repeat(5) {
      writeAction {
        workspaceModel.updateProjectModel {
          it addEntity ModuleEntity("MyModule$it", emptyList(), object : EntitySource {})
        }
      }
    }

    val updates = List(5) {
      channelUpdates.receive()
    }
    updates.forEachIndexed { index, versionedStorageChange ->
      assertEquals(index + 1, versionedStorageChange.storageAfter.entities<ModuleEntity>().toList().size)
      assertEquals(1, versionedStorageChange.getChanges(ModuleEntity::class.java).size)
    }

    job.join()
  }

  @Test(timeout = 10_000)
  fun `get first state without update`() = runBlocking {
    val channelUpdates = Channel<EntityStorage>(1)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val job = launch {
      workspaceModel.subscribe { starting, _ ->
        channelUpdates.send(starting)
      }
    }

    val storage = channelUpdates.receive()

    assertTrue(storage.entities<ModuleEntity>().none())

    job.join()
  }

  @Test(timeout = 10_000)
  fun `if one listener fails the other doesnt`() {
    var exception: IllegalStateException? = null
    var checksHappened: Unit? = null
    runBlocking {
      val channelUpdates = Channel<EntityStorage>(1)
      val fireException = CompletableDeferred<Unit>()
      val stolenScopeOfSubscribe = CompletableDeferred<CoroutineScope>()

      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
      val job = launch {
        try {
          workspaceModel.subscribe { _, _ ->
            stolenScopeOfSubscribe.complete(this)
            fireException.await()
            error("Fail")
          }
        }
        catch (ignore: IllegalStateException) {
          exception = ignore
        }
      }

      val job2 = launch {
        workspaceModel.subscribe { starting, updates ->
          channelUpdates.send(starting)
          updates.collect { channelUpdates.send(it.storageAfter) }
        }
      }

      channelUpdates.receive()

      fireException.complete(Unit)

      writeAction {
        workspaceModel.updateProjectModel {
          it addEntity NamedEntity("MyName", MySource)
        }
      }

      val updatesStorage = channelUpdates.receive()

      assertEquals("MyName", updatesStorage.entities<NamedEntity>().single().myName)
      assertFalse(job.isCancelled)
      assertFalse(job2.isCancelled)
      assertFalse(stolenScopeOfSubscribe.await().isActive)
      checksHappened = Unit

      job2.cancelAndJoin()
    }
    assertEquals("Fail", exception!!.message)
    assertNotNull(checksHappened)
  }

  @Test(timeout = 10_000)
  fun `one listener gets updates even if another is freezed`() {
    runBlocking {
      val channelUpdates = Channel<EntityStorage>(1)
      val awaitStarted = CompletableDeferred<Unit>()
      val awaitFinished = CompletableDeferred<Unit>()

      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
      val job = launch {
        workspaceModel.subscribe { _, _ ->
          awaitStarted.complete(Unit)
          delay(1.seconds)
          awaitFinished.complete(Unit)
        }
      }

      val job2 = launch {
        workspaceModel.subscribe { _, updates ->
          updates.collect { channelUpdates.send(it.storageAfter) }
        }
      }

      awaitStarted.await()

      writeAction {
        repeat(10) {
          if (awaitFinished.isCompleted) return@repeat
          workspaceModel.updateProjectModel {
            it addEntity NamedEntity("MyName$it", MySource)
          }
        }
      }

      val updatesStorage = channelUpdates.receive()

      assertTrue("MyName" in updatesStorage.entities<NamedEntity>().last().myName)

      job2.cancelAndJoin()
      job.cancelAndJoin()
    }
  }
}
