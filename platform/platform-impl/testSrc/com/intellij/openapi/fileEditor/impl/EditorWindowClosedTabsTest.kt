// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URLClassLoader

@TestApplication
internal class EditorWindowClosedTabsTest {
  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val managerFixture = projectFixture.fileEditorManagerFixture()

  @Test
  fun `the closed tabs of an unloaded class loader leave the closed-tab history`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val manager = managerFixture.get()
    val file = LightVirtualFile("closed.txt")
    manager.openFile(file, true)
    manager.openFile(LightVirtualFile("open.txt"), true)
    val window = requireNotNull(manager.currentWindow)
    window.closeFile(file)
    assertThat(window.hasClosedTabs()).isTrue()

    URLClassLoader(emptyArray(), javaClass.classLoader).use { otherClassLoader ->
      forgetClosedTabsOf(projectFixture.get(), setOf(otherClassLoader))
    }
    assertThat(window.hasClosedTabs()).isTrue()

    forgetClosedTabsOf(projectFixture.get(), setOf(file.javaClass.classLoader))
    assertThat(window.hasClosedTabs()).isFalse()
  }
}
