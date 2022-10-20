// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.UiTestRule
import com.intellij.ui.changeLafIfNeeded
import com.intellij.ui.layout.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Paths
import javax.swing.JPanel

/**
 * Nor UI DSL, neither ComponentPanelBuilder should be used to create test panels.
 * To reduce possible side-effects and make LaF tests pure.
 */
@RunWith(Parameterized::class)
class LafTest {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun lafNames() = listOf("Darcula", "IntelliJ")

    @JvmField
    @ClassRule
    val appRule = ProjectRule()

    @JvmField
    @ClassRule
    val uiRule = UiTestRule(Paths.get(PlatformTestUtil.getPlatformTestDataPath(), "ui", "laf"))
  }

  @Parameterized.Parameter
  lateinit var lafName: String

  @Rule
  @JvmField
  val testName = TestName()

  @Before
  fun beforeMethod() = runBlocking {
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      assumeTrue("macOS or Windows 10 are required", SystemInfoRt.isMac || SystemInfo.isWin10OrNewer)
    }

    changeLafIfNeeded(lafName)
  }

  @Test
  fun components() = runBlocking {
    doTest {
      createLafTestPanel()
    }
  }

  private suspend fun doTest(panelCreator: () -> JPanel) {
    withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      uiRule.validate(panelCreator(), testName, lafName)
    }
  }
}