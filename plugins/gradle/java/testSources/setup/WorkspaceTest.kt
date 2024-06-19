// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.setup

import com.intellij.ide.workspace.getAllSubprojects
import com.intellij.ide.workspace.getSubprojectByModule
import com.intellij.ide.workspace.setWorkspace
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.useProject
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WorkspaceTest: GradleTestCase() {

  @BeforeEach
  fun setUp() {
    Registry.get("ide.enable.project.workspaces").setValue(true)
  }

  @Test
  fun `manage workspace action availability in non-workspace project`() {
    val projectInfo = getSimpleProjectInfo("linked_project")
    runBlocking {
      initProject(projectInfo)
      openProject("linked_project").useProject {
        val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(it))
        ActionManager.getInstance().getAction("AddToWorkspace").update(event)
        assertFalse(event.presentation.isEnabledAndVisible)
      }
    }
  }

  @Test
  fun `get subproject by module`() {
    val projectInfo = getSimpleProjectInfo("linked_project")
    runBlocking {
      initProject(projectInfo)
      openProject("linked_project").useProject {
        setWorkspace(it)
        val module = ModuleManager.getInstance(it).modules.first()
        val subproject = getSubprojectByModule(module)
        assertNotNull(subproject)
      }
    }
  }

  @Test
  fun `not imported project`() {
    val projectInfo = getSimpleProjectInfo("linked_project")
    runBlocking {
      initProject(projectInfo)
      openProject("linked_project").useProject {
        setWorkspace(it)
        val subprojects = getAllSubprojects(it)
        assertEquals(1, subprojects.size)
        assertEquals("linked_project", subprojects.first().name)
      }
    }
  }
}