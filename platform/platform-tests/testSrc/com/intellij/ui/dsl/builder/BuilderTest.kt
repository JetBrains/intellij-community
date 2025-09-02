// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.scale.JBUIScale
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class BuilderTest {

  @Before
  fun before() {
    TestApplicationManager.getInstance()
  }

  @Test
  fun testRowCustomize() {
    val panel = panel {
      row {
        label("Label")
      }.customize(UnscaledGapsY(top = 50, bottom = 50))
    }

    assertTrue(panel.preferredSize.height > JBUIScale.scale(100), "Row customize is ignored")
  }
}