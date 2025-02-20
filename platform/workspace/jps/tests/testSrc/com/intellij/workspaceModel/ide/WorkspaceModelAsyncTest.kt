// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.application.edtWriteAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.workspaceModel.update
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import org.junit.After
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceModelAsyncTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @OptIn(DelicateCoroutinesApi::class)
  private val cs = GlobalScope.childScope("TestScope")

  @After
  fun tearDown() {
    cs.cancel()
  }

  @Test(timeout = 10_000)
  fun `check async update produce async event`() = runBlocking {
    val moduleName = "MyModule"
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    // Notifier that the listener observes the changes. If we execute the update function, before the listener is ready,
    //   the changeEvent will be emitted and the listener will hang forever.
    // However, there is a chance that the update function will be executed right after listenerIsReady and before setup of the flow
    //   listening, but in this case the test will fail by timeout and the test should be refactored at all.
    val listenerIsReady = Channel<Unit>(0)
    val collectedEventsCount = AtomicInteger()
    val workspaceModel = WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl
    val job = launch {
      assertEquals(false, application.isWriteAccessAllowed)
      listenerIsReady.send(Unit)
      workspaceModel.eventLog
        .drop(1) // Drop the first event form the previous update
        .collect { event ->
          val entityChange = (event as VersionedStorageChangeInternal).getAllChanges().single()
          assertEquals(moduleName, (entityChange.newEntity as ModuleEntity).name)
          collectedEventsCount.incrementAndGet()
        }
    }

    try {
      listenerIsReady.receive()
      delay(1_000)
      workspaceModel.update("Test add new module asynchronously") {
        it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
      }
      delay(1_000)
      assertEquals(1, collectedEventsCount.get())
    } finally {
      job.cancel()
    }
  }

  @Test
  fun `check sync update produce async event`() {
    runBlocking {
      val moduleName = "MyModule"
      val application = ApplicationManager.getApplication()
      assertEquals(false, application.isWriteAccessAllowed)

      val collectedEventsCount = AtomicInteger()
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl
      val job = launch {
        workspaceModel.eventLog
          .drop(1) // Drop the first event form the previous update
          .collect { event ->
            val entityChange = (event as VersionedStorageChangeInternal).getAllChanges().single()
            assertEquals(moduleName, (entityChange.newEntity as ModuleEntity).name)
            collectedEventsCount.incrementAndGet()
          }
      }

      try {
        edtWriteAction {
          assertEquals(true, application.isWriteAccessAllowed)
          workspaceModel.updateProjectModel("Test add new module synchronously") {
            it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
          }
        }
        delay(1_000)
        assertEquals(1, collectedEventsCount.get())
      }
      finally {
        job.cancel()
      }
    }
  }

  @Test
  fun `check async update produce sync event`() = runBlocking {
    val moduleName = "MyModule"
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    projectModel.project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        assertEquals(true, application.isWriteAccessAllowed)
        val entityChange = (event as VersionedStorageChangeInternal).getAllChanges().single()

        assertEquals(moduleName, (entityChange.newEntity as ModuleEntity).name)
      }
    })

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    workspaceModel.update("Test add new module asynchronously") {
      it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
    }
  }

  @Test
  fun `check sync update produce sync event`() {
    val moduleName = "MyModule"
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    projectModel.project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        assertEquals(true, application.isWriteAccessAllowed)
        val entityChange = (event as VersionedStorageChangeInternal).getAllChanges().single()

        assertInstanceOf<EntityChange.Added<ModuleEntity>>(entityChange)
        assertEquals(moduleName, (entityChange.newEntity as ModuleEntity).name)
      }
    })

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    runInEdtAndWait {
      runWriteActionAndWait {
        workspaceModel.updateProjectModel("Test add new module asynchronously") {
          it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
        }
      }
    }
  }

  @Test(timeout = 10_000)
  fun `check several async update produce consistent result`() = runBlocking {
    val moduleNames = setOf("ModuleA", "ModuleB", "ModuleC")
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl
    val collectedEventsCount = AtomicInteger()
    val job = launch {
      assertEquals(false, application.isWriteAccessAllowed)
      workspaceModel.eventLog
        .drop(1) // Drop the first event form the previous update
        .collect { event ->
          val entityChange = (event as VersionedStorageChangeInternal).getAllChanges().single()
          assertContains(moduleNames, (entityChange.newEntity as ModuleEntity).name)
          collectedEventsCount.incrementAndGet()
        }
    }

    try {
      moduleNames.map { moduleName ->
        launch {
          workspaceModel.update("Test add new module asynchronously") {
            it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
          }
        }
      }.joinAll()

      assertEquals(3, collectedEventsCount.get())
    } finally {
      job.cancel()
    }
  }

  @Test(timeout = 10_000)
  fun `workspace model update is available right after modification`() = runBlocking {
    val workspaceModel = projectModel.project.workspaceModel

    assertFalse(workspaceModel.currentSnapshot.contains(ModuleId("ABC")))
    workspaceModel.update {
      it addEntity ModuleEntity("ABC", emptyList(), MySource)
    }
    assertTrue(workspaceModel.currentSnapshot.contains(ModuleId("ABC")))
  }
}