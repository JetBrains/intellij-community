// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class FileEditorManagerFixtureTest {
  private val projectFixture = projectFixture()
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()

  @Test
  fun fileEditorManagerServiceIsReplaced() {
    val project = projectFixture.get()
    val manager = fileEditorManagerFixture.get()

    assertThat(FileEditorManager.getInstance(project)).isSameAs(manager)
    assertThat(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT.get(project)).isTrue()
  }

  @Test
  fun opensAndClosesVirtualFile(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val manager = fileEditorManagerFixture.get()
    val file = LightVirtualFile("test.txt", "content")

    manager.openFile(file, true)
    assertThat(manager.isFileOpen(file)).isTrue()

    manager.closeFile(file)
    assertThat(manager.isFileOpen(file)).isFalse()
  }
}
