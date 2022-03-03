// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.snapshotFileName
import com.intellij.ui.validateBounds
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import java.nio.file.Paths
import javax.swing.JPanel
import kotlin.math.max


class UiDslLayoutTest : UiDslTest() {
  companion object {
    private val testDataRoot = Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout")
  }

  private var oldScale: Float = 1.0f

  @Before
  fun setUiScale() {
    oldScale = JBUIScale.scale(1.0f)
    JBUIScale.setUserScaleFactorForTest(1.0f)
  }

  @After
  fun resetUiScale() {
    JBUIScale.setUserScaleFactorForTest(oldScale)
  }

  override fun doTest(panelCreator: () -> JPanel) {
    lateinit var panel: JPanel
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      panel = panelCreator()
      // otherwise rectangles are not set
      (panel.layout as MigLayout).isDebugEnabled = true

      val preferredSize = panel.preferredSize
      panel.setBounds(0, 0, max(preferredSize.width, JBUI.scale(480)), preferredSize.height.coerceAtLeast(320))
      panel.doLayout()
    })

    validateBounds(panel, testDataRoot, testName.snapshotFileName)
  }
}