// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.testCases

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.createJdk
import org.junit.Ignore
import org.junit.Test

class CreateJdkGuiTest : GuiTestCase() {

  private val javaHome = "JAVA_HOME"
  private val java18Home = "JAVA18_HOME"
  private val java11Home = "JAVA11_HOME"

  private fun getProperty(name: String) = System.getenv(name) ?: throw IllegalStateException("Property `$name` not set in Environment!")

  @Test
  fun createJdk18() {
    val expectedJdk = "1.8"
    val installedJdk = createJdk(getProperty(java18Home), expectedJdk)
    assert(expectedJdk == installedJdk) {
      "Expected JDK 1.8 to be installed, but $installedJdk discovered"
    }
  }

  @Test
  @Ignore
  fun createJdk11() {
    val expectedJdk = "11"
    val installedJdk = createJdk(getProperty(java11Home), expectedJdk)
    assert(expectedJdk == installedJdk) {
      "Expected JDK 11 to be installed, but $installedJdk discovered"
    }
  }

  @Test
  @Ignore
  fun createJdkFromHome() {
    createJdk(getProperty(javaHome))
  }

  override fun isIdeFrameRun() = false
}