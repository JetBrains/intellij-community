// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.JBUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@TestApplication
@RunInEdt
internal class UnifiedPluginsSearchToolbarTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun `search actions keep square preferred size inside a taller search field`() {
    val toolbar = UnifiedPluginsSearchToolbar { }
    toolbar.render(UnifiedPluginsSearchControlsState())
    val component = toolbar.component
    val filterButton = component.components.filterIsInstance<ActionButton>().single { it.isVisible }
    val extraHeight = 6

    component.setSize(component.preferredSize.width, component.preferredSize.height + extraHeight)
    component.doLayout()

    assertThat(filterButton.height).isEqualTo(filterButton.preferredSize.height)
    assertThat(filterButton.width).isEqualTo(filterButton.preferredSize.width)
    assertThat(filterButton.x).isZero()
    assertThat(filterButton.y).isEqualTo(extraHeight / 2)
    assertThat(component.width - filterButton.x - filterButton.width).isEqualTo(JBUI.scale(3))
  }

  @Test
  fun `visible sort and filter actions keep four pixel gap`() {
    val toolbar = UnifiedPluginsSearchToolbar { }
    toolbar.render(UnifiedPluginsSearchControlsState(sortVisible = true))
    val component = toolbar.component
    val buttons = component.components.filterIsInstance<ActionButton>().filter { it.isVisible }
    assertThat(buttons).hasSize(2)
    val sortButton = buttons[0]
    val filterButton = buttons[1]

    component.size = component.preferredSize
    component.doLayout()

    assertThat(filterButton.x - sortButton.x - sortButton.width).isEqualTo(JBUI.scale(4))
    assertThat(component.width - filterButton.x - filterButton.width).isEqualTo(JBUI.scale(3))
  }
}
