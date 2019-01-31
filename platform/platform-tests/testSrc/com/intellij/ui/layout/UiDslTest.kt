// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.async.coroutineDispatchingContext
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.UiTestRule
import com.intellij.ui.changeLafIfNeed
import com.intellij.ui.layout.migLayout.patched.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.Assume.assumeTrue
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

    @JvmField
    @ClassRule
    val appRule = ProjectRule()

    @JvmField
    @ClassRule
    val uiRule = UiTestRule(Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout"))

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
  fun beforeMethod() = runBlocking {
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      // let's for now to see how it is going on macOS
      assumeTrue("macOS or Windows 10 are required", SystemInfo.isMacOSHighSierra /* || SystemInfo.isWin10OrNewer */)
    }

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

  @Test
  fun `vertical buttons`() {
    doTest { withVerticalButtons() }
  }

  @Test
  fun `do not add visual paddings for titled border`() {
    doTest { commentAndPanel() }
  }

  @Test
  fun `titled rows`() {
    // failed on TC but for now no 10.14 agents to test
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      // let's for now to see how it is going on macOS
      assumeTrue("macOS 10.14 or Windows 10 are required", SystemInfo.isMacOSMojave /* || SystemInfo.isWin10OrNewer */)
    }

    doTest { titledRows() }
  }

  @Test
  fun `titled row`() {
    // failed on TC but for now no 10.14 agents to test
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      // let's for now to see how it is going on macOS
      assumeTrue("macOS 10.14 or Windows 10 are required", SystemInfo.isMacOSMojave /* || SystemInfo.isWin10OrNewer */)
    }

    doTest { titledRow() }
  }

  private fun doTest(panelCreator: () -> JPanel) {
    runBlocking {
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        val panel = panelCreator()
        // otherwise rectangles are not set
        (panel.layout as MigLayout).isDebugEnabled = true
        uiRule.validate(panel, testName, lafName)
      }
    }
  }
}