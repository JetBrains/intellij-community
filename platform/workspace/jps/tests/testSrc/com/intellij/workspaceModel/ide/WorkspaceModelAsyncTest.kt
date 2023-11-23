// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.application.writeAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import org.junit.After
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
  private val cs = GlobalScope.namedChildScope("TestScope")

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

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val job = cs.launch {
      assertEquals(false, application.isWriteAccessAllowed)
      listenerIsReady.send(Unit)
      val entityChange = workspaceModel.changesEventFlow.first().getAllChanges().single()
      assertInstanceOf<EntityChange.Added<ModuleEntity>>(entityChange)

      assertEquals(moduleName, (entityChange.newEntity as ModuleEntity).name)
    }

    listenerIsReady.receive()
    workspaceModel.update("Test add new module asynchronously") {
      it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
    }

    job.join()
  }

  @Test
  fun `check sync update produce async event`() = runBlocking {
    val moduleName = "MyModule"
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val job = cs.launch {
      assertEquals(false, application.isWriteAccessAllowed)
      val entityChange = workspaceModel.changesEventFlow.first().getAllChanges().single()
      assertInstanceOf<EntityChange.Added<ModuleEntity>>(entityChange)

      assertEquals(moduleName, (entityChange.newEntity as ModuleEntity).name)
    }

    writeAction {
      assertEquals(true, application.isWriteAccessAllowed)
      workspaceModel.updateProjectModel("Test add new module synchronously") {
        it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
      }
    }

    job.join()
  }

  @Test
  fun `check async update produce sync event`() = runBlocking {
    val moduleName = "MyModule"
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    projectModel.project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        assertEquals(true, application.isWriteAccessAllowed)
        val entityChange = event.getAllChanges().single()

        assertInstanceOf<EntityChange.Added<ModuleEntity>>(entityChange)
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
        val entityChange = event.getAllChanges().single()

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

  @Test
  fun `check several async update produce consistent result`() = runBlocking {
    val moduleNames = setOf("ModuleA", "ModuleB", "ModuleC")
    val application = ApplicationManager.getApplication()
    assertEquals(false, application.isWriteAccessAllowed)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val job = cs.launch {
      assertEquals(false, application.isWriteAccessAllowed)
      workspaceModel.changesEventFlow.take(3).collect { storageChange ->
        val entityChange = storageChange.getAllChanges().single()
        assertContains(moduleNames, (entityChange.newEntity as ModuleEntity).name)
      }
    }

    moduleNames.map { moduleName ->
      launch {
        workspaceModel.update("Test add new module asynchronously") {
          it addEntity ModuleEntity(moduleName, emptyList(), object : EntitySource {})
        }
      }
    }.joinAll()

    job.join()
  }
}