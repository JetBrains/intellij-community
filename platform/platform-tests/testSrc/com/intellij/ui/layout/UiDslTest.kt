// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.UiTestRule
import com.intellij.ui.changeLafIfNeed
import net.miginfocom.layout.LayoutUtil
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Paths
import javax.swing.JPanel

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if need.
 */
@RunWith(Parameterized::class)
class UiDslTest {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun lafNames() = listOf("Darcula", "IntelliJ")

    private val uiRule = UiTestRule(Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout"))

    init {
      System.setProperty("idea.ui.set.password.echo.char", "true")
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @Parameterized.Parameter
  lateinit var lafName: String

  @Rule
  @JvmField
  val testName = TestName()

  @Before
  fun beforeMethod() {
    assumeTrue("Not CI server or not Linux", !UsefulTestCase.IS_UNDER_TEAMCITY || !SystemInfoRt.isLinux)
    assumeTrue("Not Windows or Windows 10", !SystemInfoRt.isWindows || SystemInfo.isWin10OrNewer)

    System.setProperty("idea.ui.comment.copyable", "false")
    changeLafIfNeed(lafName)
  }

  @After
  fun afterMethod() {
    System.clearProperty("idea.ui.comment.copyable")
  }

  @Test
  fun `align fields in the nested grid`() {
    doTest { alignFieldsInTheNestedGrid() }
  }

  @Test
  fun `align fields`() {
    doTest { labelRowShouldNotGrow() }
  }

  @Test
  fun cell() {
    doTest { cellPanel() }
  }

  @Test
  fun `note row in the dialog`() {
    doTest { noteRowInTheDialog() }
  }

  @Test
  fun `visual paddings`() {
    doTest { visualPaddingsPanel() }
  }

  private fun doTest(panelCreator: () -> JPanel) {
    invokeAndWaitIfNeed {
      // otherwise rectangles are not set
      LayoutUtil.setGlobalDebugMillis(1000)
      val panel = panelCreator()
      try {
        uiRule.validate(panel, testName, lafName)
      }
      finally {
        LayoutUtil.setGlobalDebugMillis(0)
        // as result, MigLayout will stop debug timer
        panel.doLayout()
      }
    }
  }
}