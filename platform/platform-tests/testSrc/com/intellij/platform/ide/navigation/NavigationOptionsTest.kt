// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
internal class NavigationOptionsTest {
  companion object {
    private val project = projectFixture()
    private val file = project.moduleFixture().sourceRootFixture().psiFileFixture("target.txt", "target")
  }

  private val editor = file.editorFixture()

  @Test
  fun `missing context options use defaults`() {
    val context: DataContext? = null
    assertEquals(NavigationOptions.defaultOptions(), context.toNavigationOptions())
    assertEquals(NavigationOptions.defaultOptions(), DataContext.EMPTY_CONTEXT.toNavigationOptions())
  }

  @Test
  fun `context options retain their fields and include the context editor`() {
    val options = NavigationOptions.defaultOptions().requestFocus(false).openInRightSplit(true).preserveCaret(true)
      .recordAsBackHistory(false).caretPlacement(CaretPlacement.TOKEN_END).forcePreviewTab(true)

    assertEquals(options.requestedEditor(RequestedEditor.Specific(editor.get())), context(options).toNavigationOptions())
  }

  @Test
  fun `explicit options take priority over context options`() {
    val context = context(NavigationOptions.defaultOptions().openInRightSplit(true).requestFocus(false))
    val options = NavigationOptions.defaultOptions()

    assertEquals(options.requestedEditor(RequestedEditor.Specific(editor.get())), context.toNavigationOptions(options))
    val withoutEditor = options.requestedEditor(RequestedEditor.None)
    assertSame(withoutEditor, context.toNavigationOptions(withoutEditor))
  }

  @Test
  fun `context options can exclude the context editor`() {
    val options = NavigationOptions.defaultOptions().requestedEditor(RequestedEditor.None)

    assertSame(options, context(options).toNavigationOptions())
  }

  private fun context(options: NavigationOptions): DataContext = SimpleDataContext.builder()
    .add(NavigationOptions.KEY, options)
    .add(OpenFileDescriptor.NAVIGATE_IN_EDITOR, editor.get())
    .build()
}
