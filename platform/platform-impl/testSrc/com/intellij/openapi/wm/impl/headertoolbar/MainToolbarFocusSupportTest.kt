// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.ClientProperty
import com.intellij.util.ref.GCWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Dimension
import javax.swing.JPanel

/**
 * The focus support of the main toolbar ends the registrations of a header toolbar when the toolbar leaves the main toolbar.
 * The main toolbar lives as long as its frame, so a registration that outlives a header toolbar keeps the actions of that toolbar.
 */
@TestApplication
@RunInEdt
internal class MainToolbarFocusSupportTest {
  @Test
  fun `a header toolbar that leaves the main toolbar keeps no registration of the focus support`(@TestDisposable disposable: Disposable) {
    val mainToolbar = JPanel()
    MainToolbarFocusSupport(toolbar = mainToolbar, parentDisposable = disposable).registerComponent(mainToolbar)

    addAndRemoveHeaderToolbar(mainToolbar).ensureCollected()
  }

  /** Adds a header toolbar with one button to [mainToolbar], removes the toolbar again, and answers a watcher of the toolbar. */
  private fun addAndRemoveHeaderToolbar(mainToolbar: JPanel): GCWatcher {
    val action = DumbAwareAction.create {}
    val headerToolbar = ActionManager.getInstance().createActionToolbar(PLACE, DefaultActionGroup(action), true).component
    val button = ActionButton(action, Presentation(), PLACE, Dimension(10, 10))
    headerToolbar.add(button)
    mainToolbar.add(headerToolbar)
    assertThat(ClientProperty.get(button, AnAction.ACTIONS_KEY).orEmpty()).hasSize(1)

    mainToolbar.remove(headerToolbar)
    assertThat(ClientProperty.get(button, AnAction.ACTIONS_KEY).orEmpty()).isEmpty()
    return GCWatcher.tracking(headerToolbar)
  }
}

private const val PLACE = "MainToolbarFocusSupportTest"
