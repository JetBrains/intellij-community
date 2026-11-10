// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Calls [LeakHunter] on a background thread, with no read action, while a project is open.
 */
@TestApplication
class LeakHunterBackgroundThreadTest {
  private val project by projectFixture(openAfterCreation = true)

  @BeforeEach
  fun checkPreconditions() {
    assertThat(ProjectManager.getInstance().openProjects).contains(project)
    assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
    assertThat(ApplicationManager.getApplication().isReadAccessAllowed).isFalse()
  }

  @Test
  fun `no leak`() {
    LeakHunter.checkLeak(Any(), BackgroundLeak::class.java)
  }

  @Test
  fun `leak is reported`() {
    val error = assertThrows<AssertionError> {
      LeakHunter.checkLeak(BackgroundLeakRoot(BackgroundLeak()), BackgroundLeak::class.java)
    }
    assertThat(error).hasMessageContaining("Found a leaked instance of")
  }
}

private class BackgroundLeak

@Suppress("unused")
private class BackgroundLeakRoot(val leak: BackgroundLeak)
