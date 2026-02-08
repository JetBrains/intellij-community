// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@TestApplication
class HeavyPlatformUtilitiesTest {
  val project = projectFixture(openAfterCreation = true)
  val module = project.moduleFixture()
  val sourceRoot = module.sourceRootFixture()
  val file = sourceRoot.psiFileFixture("A.java", """class A {
    |  String s = "abcde";
    |  String f = "___abcde___";
    |}""".trimMargin())

  @Test
  fun `waitUntilIndexesReady works with background WA`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val project = project.get()
    val dumbActionCompleted = AtomicBoolean(false)
    DumbService.getInstance(project).queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        application.invokeAndWait {
          dumbActionCompleted.set(true)
        }
      }
    })
    launch(Dispatchers.Default) {
      backgroundWriteAction {
      }
    }
    Thread.sleep(100) // do not release EDT event
    Assertions.assertFalse(dumbActionCompleted.get())
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    Assertions.assertTrue(dumbActionCompleted.get())
  }

  @Test
  fun `PsiSearchHelper#processElementsWithWord can be canceled on project scope`() = `PsiSearchHelperImpl cancellation test`(GlobalSearchScope.projectScope(project.get()))

  fun `PsiSearchHelperImpl cancellation test`(scope: SearchScope): Unit = timeoutRunBlocking {
    val prj = project.get()
    val j1 = Job(coroutineContext.job)
    val j2 = Job(coroutineContext.job)
    val counter = AtomicInteger(0)
    val textProcessingJob = launch(Dispatchers.Default) {
      PsiSearchHelper.getInstance(prj).processElementsWithWord(
        { _, _ ->
          val counterValue = counter.getAndIncrement()
          if (counterValue == 0) {
            j1.complete()
            j2.asCompletableFuture().join()
          }
          if (counterValue == 1) {
            error("Should not be reached")
          }
          true
        }, scope, "abcde",
        UsageSearchContext.ANY, false)
    }
    j1.join()
    textProcessingJob.cancel()
    j2.complete()
    textProcessingJob.join()
    assertEquals(1, counter.get())
  }
}