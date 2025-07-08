// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class ActionDumbAwareTest {

  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val project get() = projectModel.project

  private class TestAction() : AnAction() {
    var isPerformed: Boolean = false
      private set
    var isUpdated: Boolean = false
      private set

    override fun actionPerformed(e: AnActionEvent) {
      isPerformed = true
    }

    override fun update(e: AnActionEvent) {
      isUpdated = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private class TestDumbAwareAction() : DumbAwareAction() {
    var isPerformed: Boolean = false
      private set
    var isUpdated: Boolean = false
      private set

    override fun actionPerformed(e: AnActionEvent) {
      isPerformed = true
    }

    override fun update(e: AnActionEvent) {
      isUpdated = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  @BeforeEach
  fun `wait for smart mode`() {
    DumbService.getInstance(project).waitForSmartMode()
  }

  @Test
  fun `action is performed`(): Unit = runBlocking {
    val action = TestAction()
    val event = createEvent(null)

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.performAction(action, event) }
    assertTrue(action.isPerformed)
  }

  @Test
  fun `ignore isIndexable when file is on non-local filesystem`(): Unit = runBlocking {
    val action = TestAction()
    val event = createEvent(LightVirtualFile("non-indexable.txt"))

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.performAction(action, event) }
    assertTrue(action.isPerformed)
  }

  @Test
  fun `action is not performed when file is non-indexable`(): Unit = runBlocking {
    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()

    val file = projectModel.baseProjectDir.newVirtualFile("non-indexable.txt")
    workspaceModel.update("add a non-indexable file") { storage ->
      storage.addEntity(NonIndexableTestEntity(file.toVirtualFileUrl(urlManager), NonPersistentEntitySource))
    }

    val action = TestAction()
    val event = createEvent(file)

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.performAction(action, event) }
    assertFalse(action.isPerformed)
  }

  @Test
  fun `dumb-aware action is performed when file is non-indexable`() = runBlocking {
    val action = TestDumbAwareAction()
    val event = createEvent(LightVirtualFile("non-indexable.txt"))

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.performAction(action, event) }
    assertTrue(action.isPerformed)
  }

  @Test
  fun `action is performed when file is indexable`(): Unit = runBlocking {
    val baseDir = projectModel.baseProjectDir.newVirtualDirectory("content")
    val file = projectModel.baseProjectDir.newVirtualFile("content/file.txt")

    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    workspaceModel.update("adsf") { storage ->
      storage.addEntity(ContentRootEntity(baseDir.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource) {
        module = ModuleEntity("test", emptyList(), NonPersistentEntitySource)
      })
    }

    val action = TestAction()
    val event = createEvent(file)

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.performAction(action, event) }
    assertTrue(action.isPerformed)
  }

  @Test
  fun `action is not updated when file is non-indexable`(): Unit = runBlocking {
    val action = TestAction()
    val event = createEvent(LightVirtualFile("non-indexable.txt"))

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.updateAction(action, event) }
    assertTrue(action.isUpdated)
  }

  @Test
  fun `dumb-aware action is updated when file is non-indexable`(): Unit = runBlocking {
    val action = TestDumbAwareAction()
    val event = createEvent(LightVirtualFile("non-indexable.txt"))

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.updateAction(action, event) }
    assertTrue(action.isUpdated)
  }

  @Test
  fun `action is updated when file is indexable`(): Unit = runBlocking {
    val baseDir = projectModel.baseProjectDir.newVirtualDirectory("content")
    val file = projectModel.baseProjectDir.newVirtualFile("content/file.txt")

    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    workspaceModel.update("adsf") { storage ->
      storage.addEntity(ContentRootEntity(baseDir.toVirtualFileUrl(urlManager), emptyList(), NonPersistentEntitySource) {
        module = ModuleEntity("test", emptyList(), NonPersistentEntitySource)
      })
    }

    val action = TestAction()
    val event = createEvent(file)

    readAction { ActionUtil.updateAction(action, event) }
    runInEdtAndWait { ActionUtil.updateAction(action, event) }
    assertTrue(action.isUpdated)
  }


  private fun createEvent(file: VirtualFile?): AnActionEvent {
    val context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[CommonDataKeys.PROJECT] = project
      sink[CommonDataKeys.VIRTUAL_FILE] = file
    }
    return TestActionEvent.createTestEvent(context)
  }
}