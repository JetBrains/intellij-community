// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.UI
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.keymap.impl.KeymapImpl
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@TestApplication
@RegistryKey(key = "ide.switcher.tool.window.list", value = "true")
internal class FrontendSwitcherItemsCollectorTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `windows with the same number shortcut remain in the list`(): Unit = timeoutRunBlocking {
    val ids = listOf("FirstWindow", "SecondWindow")
    val windows = collect(ids)

    assertEquals(ids.toSet(), windows.map { it.id }.toSet())
    assertEquals("2", windows.single { it.id == ids.first() }.mnemonic)
    assertEquals(2, windows.mapNotNull { it.mnemonic }.toSet().size)
  }

  private suspend fun collect(ids: List<String>): List<SwitcherToolWindow> {
    val project = projectFixture.get()
    return withContext(Dispatchers.UI) {
      val windows = ids.map { id ->
        object : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
          override fun getId(): String = id
          override fun getStripeTitle(): String = id
          override fun isAvailable(): Boolean = true
        }
      }
      val manager = object : ToolWindowHeadlessManagerImpl(project) {
        override val toolWindows: List<ToolWindow>
          get() = windows
      }
      project.registerOrReplaceServiceInstance(ToolWindowManager::class.java, manager, disposable)
      val keymap = KeymapImpl().apply {
        name = "FrontendSwitcherItemsCollectorTest"
        val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK), null)
        for (id in ids) {
          addShortcut(ActivateToolWindowAction.Manager.getActionIdForToolWindow(id), shortcut)
        }
      }
      val keymapManager = KeymapManagerEx.getInstanceEx()
      val previousKeymap = keymapManager.activeKeymap
      keymapManager.schemeManager.addScheme(keymap)
      try {
        keymapManager.activeKeymap = keymap
        collectToolWindows(false, true, false, SwitcherMnemonicsRegistry(SwitcherLaunchEventParameters(null)), project)
      }
      finally {
        keymapManager.activeKeymap = previousKeymap
        keymapManager.schemeManager.removeScheme(keymap)
      }
    }
  }
}
