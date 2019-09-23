// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.UiTestRule
import com.intellij.ui.changeLafIfNeeded
import com.intellij.ui.layout.migLayout.patched.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Paths
import javax.swing.JPanel

@RunWith(Parameterized::class)
@Ignore
class UiDslRenderingTest : UiDslTest() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun lafNames() = listOf("Darcula", "IntelliJ")

    @JvmField
    @ClassRule
    val uiRule = UiTestRule(Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "layout"))
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @Parameterized.Parameter
  lateinit var lafName: String

  @Before
  fun beforeMethod() = runBlocking {
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      // let's for now to see how it is going on macOS
      Assume.assumeTrue("macOS or Windows 10 are required", SystemInfo.isMacOSHighSierra /* || SystemInfo.isWin10OrNewer */)
    }

    System.setProperty("idea.ui.comment.copyable", "false")
    changeLafIfNeeded(lafName)
  }

  override fun doTest(panelCreator: () -> JPanel) {
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