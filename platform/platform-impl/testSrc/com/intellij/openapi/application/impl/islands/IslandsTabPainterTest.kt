// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import javax.swing.JPanel

@TestApplication
internal class IslandsTabPainterTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `configured first tab offset survives painting`(): Unit =
    timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val tabs = JBTabsImpl(null, disposable)
      val expectedOffset = JBUI.scale(12)
      tabs.setFirstTabOffset(expectedOffset)
      val tab = tabs.addTab(TabInfo(JPanel()).setText("Overview"))
      tabs.setBounds(0, 0, JBUI.scale(400), JBUI.scale(300))
      tabs.doLayout()

      val image = BufferedImage(tabs.width, tabs.height, BufferedImage.TYPE_INT_ARGB)
      val graphics = image.createGraphics()
      try {
        IslandsTabPainterAdapter(isDefault = true, debugger = false, isEnabled = true)
          .paintBackground(checkNotNull(tabs.getTabLabel(tab)), graphics, tabs)
      }
      finally {
        graphics.dispose()
      }

      assertThat(tabs.getFirstTabOffset()).isEqualTo(expectedOffset)
    }
}
