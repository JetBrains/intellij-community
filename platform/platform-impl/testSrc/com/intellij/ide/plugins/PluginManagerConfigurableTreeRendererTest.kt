// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.Badge
import com.intellij.ui.treeStructure.SimpleTree
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.AncestorEvent

@TestApplication
@Timeout(30)
internal class PluginManagerConfigurableTreeRendererTest {
  @Test
  fun `update count uses a gray secondary badge and hides zero`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UI) {
      val renderer = PluginManagerConfigurableTreeRenderer()
      val tree = SimpleTree()
      renderer.accept(PluginUpdatesEvent(listOf(PluginDto("Update", PluginId.getId("update"))), emptyList(), emptyList()))
      try {
        val decorator = checkNotNull(renderer.getDecorator(tree, null, false))
        val label = decorator.first as JLabel
        val badge = label.icon as Badge

        assertThat(badge.text).isEqualTo("1")
        assertThat(badge.colorType).isEqualTo(Badge.ColorType.GRAY_SECONDARY)
        assertThat(label.accessibleContext.accessibleName).isEqualTo("1")

        renderer.accept(PluginUpdatesEvent(emptyList(), emptyList(), emptyList()))
        assertThat(renderer.getDecorator(tree, null, false)).isNull()
      }
      finally {
        renderer.ancestorRemoved(AncestorEvent(tree, AncestorEvent.ANCESTOR_REMOVED, JPanel(), null))
      }
    }
}
