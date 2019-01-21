// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.wm

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test

class CloseProjectWindowHelperTest {
  @Test
  fun `on Windows closing last project leads to exit`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = false

      override fun getNumberOfOpenedProjects() = 1
    }

    helper.windowClosing(null)
    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
  }

  @Test
  fun `on macOS closing last project leads to show welcome screen`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun getNumberOfOpenedProjects() = 1
    }

    helper.windowClosing(null)
    assertThat(helper.wasQuitAppCalled).isFalse()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isTrue()
  }

  // well, not clear is listener will be called for case when no opened  prtojects atllall, but just t o be sure
  @Test
  fun `on Windows closing if no opened projects leads to exit`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = false

      override fun getNumberOfOpenedProjects() = 0
    }

    helper.windowClosing(null)
    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
  }

  @Test
  fun `on macOS closing if no opened projects leads to exit`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun getNumberOfOpenedProjects() = 0
    }

    helper.windowClosing(null)
    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
  }
}

open class TestCloseProjectWindowHelper : CloseProjectWindowHelper() {
  var wasQuitAppCalled = false
    private set

  var wasShowWelcomeFrameIfNoProjectOpenedCalled = false
    private set

  override val isShowWelcomeScreenFromSettings = true

  override fun quitApp() {
    assertThat(wasQuitAppCalled).isFalse()
    wasQuitAppCalled = true
  }

  override fun closeProjectAndShowWelcomeFrameIfNoProjectOpened(project: Project?) {
    assertThat(wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
    wasShowWelcomeFrameIfNoProjectOpenedCalled = true
  }
}