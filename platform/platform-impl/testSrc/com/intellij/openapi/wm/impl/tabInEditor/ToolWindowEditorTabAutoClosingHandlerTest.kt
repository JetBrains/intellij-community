// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests that the editor tab limit never closes a tool window editor tab on its own. A closed tab would release its
 * content, for example a running terminal session, without the user asking for it.
 */
@TestApplication
class ToolWindowEditorTabAutoClosingHandlerTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture(initDockableContentFactory = true)

  private val project: Project get() = projectFixture.get()
  private val manager: FileEditorManagerImpl get() = fileEditorManagerFixture.get()

  @Test
  fun `the tab limit closes plain files but never a tool window editor tab`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val settings = UISettings.getInstance()
      val storedLimit = settings.editorTabLimit
      settings.editorTabLimit = 1
      try {
        val tabFile = createTabFile(project = project, toolWindowId = "TestToolWindow")
        val firstPlainFile = LightVirtualFile("first.txt")
        val secondPlainFile = LightVirtualFile("second.txt")

        manager.openFile(tabFile, true)
        manager.openFile(firstPlainFile, true)
        manager.openFile(secondPlainFile, true)

        // The limit is active: the oldest plain file made room for the second one. The tab does not count and stays.
        assertThat(manager.isFileOpen(firstPlainFile)).isFalse()
        assertThat(manager.isFileOpen(secondPlainFile)).isTrue()
        assertThat(manager.isFileOpen(tabFile)).isTrue()
      }
      finally {
        settings.editorTabLimit = storedLimit
      }
    }
}
