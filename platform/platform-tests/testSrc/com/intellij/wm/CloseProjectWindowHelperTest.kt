// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.wm

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.runInEdtAndWait
import org.junit.ClassRule
import org.junit.Test

class CloseProjectWindowHelperTest {

  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun `on Windows closing last project leads to exit`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = false

      override fun getNumberOfOpenedProjects() = 1
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
  }

  @Test
  fun `on macOS closing last project leads to show welcome screen`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun getNumberOfOpenedProjects() = 1
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isFalse()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isTrue()
  }

  // well, not clear is listener will be called for the case when no opened projects at all, but just to be sure
  @Test
  fun `on Windows closing if no opened projects leads to exit`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = false

      override fun getNumberOfOpenedProjects() = 0
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
  }

  @Test
  fun `on macOS closing if no opened projects leads to exit`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun getNumberOfOpenedProjects() = 0
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isFalse()
  }

  @Test
  fun `on macOS closing a tab with tabbed project view`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun isMacOsTabbedProjectView(project: Project?): Boolean = true
      override fun isCloseTab(project: Project?): Boolean = true
      override fun couldReturnToWelcomeScreen(projects: Array<Project>): Boolean = false
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isFalse()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isTrue()
  }

  @Test
  fun `on macOS closing an application with tabbed project view when should show welcome screen`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun isMacOsTabbedProjectView(project: Project?): Boolean = true
      override fun isCloseTab(project: Project?): Boolean = false
      override fun couldReturnToWelcomeScreen(projects: Array<Project>): Boolean = true
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isFalse()
    assertThat(helper.wasShowWelcomeFrameIfNoProjectOpenedCalled).isTrue()
  }

  @Test
  fun `on macOS closing an application with tabbed project view when should not show welcome screen`() {
    val helper = object : TestCloseProjectWindowHelper() {
      override val isMacSystemMenu = true

      override fun isMacOsTabbedProjectView(project: Project?): Boolean = true
      override fun isCloseTab(project: Project?): Boolean = false
      override fun couldReturnToWelcomeScreen(projects: Array<Project>): Boolean = false
    }

    runInEdtAndWait {
      helper.windowClosing(null)
    }

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