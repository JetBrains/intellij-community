// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.ui.LafManager
import com.intellij.testFramework.ProjectRule
import org.junit.*
import org.junit.rules.TestName
import javax.swing.JPanel

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if needed.
 */
abstract class UiDslTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ProjectRule()

    init {
      System.setProperty("idea.ui.set.password.echo.char", "true")
    }
  }

  @Rule
  @JvmField
  val testName = TestName()

  @Before
  fun initializeLAF() {
    LafManager.getInstance()
  }

  @After
  fun afterMethod() {
    System.clearProperty("idea.ui.comment.copyable")
  }

  @Test
  fun `titled row`() {
    doTest { titledRow() }
  }

  protected abstract fun doTest(panelCreator: () -> JPanel)
}