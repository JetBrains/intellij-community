// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.showcase.resources.ResourceCounter
import com.intellij.testFramework.junit5.showcase.resources.project.JUnit5TestLevelProject.Companion.counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test


@Service(Service.Level.PROJECT)
private class MyProjectService(private val scope: CoroutineScope) {
  init {
    scope.launch {
      while (true) {
        delay(100) // Should be stopped after the test. Leakage will be reported
      }
    }
  }

  fun regCodeOnDispose(code: () -> Unit) {
    scope.coroutineContext.job.invokeOnCompletion {
      code()
    }
  }
}

/**
 * Class-level project created once per test class.
 * [counter] ensures it doesn't leak
 */
@TestApplication
class JUnit5TestLevelProject {

  companion object {
    @AfterAll
    @JvmStatic
    fun ensureEmpty() {
      counter.ensureEmpty()
    }

    @JvmStatic
    private val counter = ResourceCounter()

  }

  private val projectFixture = projectFixture()

  @Test
  fun test() {
    val project1 = projectFixture.get()
    val project2 = projectFixture.get()
    assertEquals(project1, project2, "Class level must use same resource")
    assertFalse(project1.isDisposed)
    Disposer.register(project1) {

    }
    counter.acquire()
    project1.service<MyProjectService>().regCodeOnDispose {
      counter.release()
    }
  }
}