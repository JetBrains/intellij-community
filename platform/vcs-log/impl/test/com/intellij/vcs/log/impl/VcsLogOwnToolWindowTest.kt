// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The main Log of a project is only in a tool window of that project. A frame guest gets the Version Control tool window of its
 * frame owner from its tool window manager, and the main Log paths of the guest must not take it.
 */
@TestApplication
internal class VcsLogOwnToolWindowTest {
  private val owner by projectFixture()
  private val guest by projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the tool window of the project is its own tool window`() {
    val toolWindow = registerVcsToolWindow(owner)

    assertThat(VcsLogContentUtil.getOwnToolWindow(owner)).isSameAs(toolWindow)
  }

  @Test
  fun `the tool window of another project is not the own tool window`() {
    val toolWindow = registerVcsToolWindow(owner)
    // as the manager of a frame guest, which gives the tool window of the frame owner
    val ownerManager = ToolWindowManager.getInstance(owner)
    guest.replaceService(ToolWindowManager::class.java, object : ToolWindowHeadlessManagerImpl(guest) {
      override fun getToolWindow(id: String?): ToolWindow? = ownerManager.getToolWindow(id)
    }, disposable)

    assertThat(VcsLogContentUtil.getToolWindow(guest)).isSameAs(toolWindow)
    assertThat(VcsLogContentUtil.getOwnToolWindow(guest)).isNull()
  }

  @Test
  fun `a project without the tool window has no own tool window`() {
    assertThat(VcsLogContentUtil.getOwnToolWindow(guest)).isNull()
  }

  private fun registerVcsToolWindow(project: Project): ToolWindow {
    val manager = ToolWindowManager.getInstance(project) as ToolWindowHeadlessManagerImpl
    val toolWindow = manager.doRegisterToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    @Suppress("DEPRECATION")
    Disposer.register(disposable) { manager.unregisterToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) }
    return toolWindow
  }
}
