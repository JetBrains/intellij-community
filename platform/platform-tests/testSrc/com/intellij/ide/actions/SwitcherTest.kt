// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.Switcher.SwitcherPanel.Companion.getFilesSelectedIndexForTest
import com.intellij.ide.actions.Switcher.SwitcherPanel.Companion.getFilesToShowForTest
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import org.assertj.core.api.Assertions.assertThat
import javax.swing.SwingConstants

class SwitcherTest : FileEditorManagerTestCase() {
  override fun getTestDataPath(): String = PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager"

  fun testSwitcherPanelWithTabs() {
    testTabPlacement(tabPlacement = SwingConstants.TOP, goForward = true)
    testTabPlacement(tabPlacement = SwingConstants.TOP, goForward = false)
  }

  fun testSwitcherPanelWithoutTabs() {
    testTabPlacement(tabPlacement = UISettings.TABS_NONE, goForward = true)
    testTabPlacement(tabPlacement = UISettings.TABS_NONE, goForward = false)
  }

  private fun testTabPlacement(tabPlacement: Int, goForward: Boolean) {
    getInstance().state.editorTabPlacement = tabPlacement
    manager!!.openFile(getFile("/src/1.txt"), null, FileEditorOpenOptions(requestFocus = true))
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
    manager!!.openFile(getFile("/src/2.txt"), null, FileEditorOpenOptions(requestFocus = true))
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
    manager!!.openFile(getFile("/src/3.txt"), null, FileEditorOpenOptions(requestFocus = true))
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

    val filesToShow = getFilesToShowForTest(project)
    val selectedItem = getFilesSelectedIndexForTest(project, goForward)


    assertEquals(if (goForward) 1 else 2, selectedItem)
    assertEquals(3, filesToShow.size)

    assertThat(filesToShow).containsExactly(
      getFile("/src/3.txt"),
      getFile("/src/2.txt"),
      getFile("/src/1.txt"),
    )
  }
}
