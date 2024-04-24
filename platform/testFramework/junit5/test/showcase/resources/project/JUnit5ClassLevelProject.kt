// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.asExtension
import com.intellij.testFramework.junit5.resources.create
import com.intellij.testFramework.junit5.resources.providers.PathInfo
import com.intellij.testFramework.junit5.resources.providers.ProjectProvider
import com.intellij.testFramework.junit5.showcase.resources.ResourceCounter
import com.intellij.testFramework.junit5.showcase.resources.project.JUnit5ClassLevelProject.Companion.counter
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path


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
class JUnit5ClassLevelProject {

  companion object {
    @Order(0)
    @JvmStatic
    @RegisterExtension
    val leak = AfterAllCallback {
      counter.ensureEmpty()
    }

    @Order(1)
    @JvmStatic
    @RegisterExtension
    val ext = ProjectProvider().asExtension()
    private val counter = ResourceCounter()
  }

  @Test
  fun test(project1: Project, project2: Project) {
    assertEquals(project1, project2, "Class level must use same resource")
    assertFalse(project1.isDisposed)
    counter.acquire()
    project1.service<MyProjectService>().regCodeOnDispose {
      counter.release()
    }
  }

  @Test
  fun testManualDestroy(@TempDir tempDir: Path): Unit = runBlocking {
    val project = ext.create(PathInfo(tempDir, deletePathOnExit = false), disposeOnExit = true)
    assertEquals(tempDir, project.basePath!!.toNioPathOrNull()!!)
    Assertions.assertTrue(ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false))
  }

  @Test
  fun testManualResource(): Unit = runBlocking {
    // Manually created projects are always different
    val p1 = ext.create()
    val p2 = ext.create()
    Assertions.assertNotEquals(p1, p2)
  }
}