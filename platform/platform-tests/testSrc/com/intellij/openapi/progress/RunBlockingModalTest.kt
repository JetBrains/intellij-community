// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
class RunBlockingModalTest {

  @Test
  fun `normal completion`(): Unit = timeoutRunBlocking {
    val result = withContext(Dispatchers.EDT) {
      runBlockingModal(ModalTaskOwner.guess(), "") { 42 }
    }
    assertEquals(42, result)
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    val t: Throwable = object : Throwable() {}
    withContext(Dispatchers.EDT) {
      val thrown = assertThrows<Throwable> {
        runBlockingModal<Unit>(ModalTaskOwner.guess(), "") {
          throw t // fail the scope
        }
      }
      assertSame(t, thrown)
    }
  }

  @Test
  fun nested(): Unit = timeoutRunBlocking {
    val result = withContext(Dispatchers.EDT) {
      runBlockingModal(ModalTaskOwner.guess(), "") {
        withContext(Dispatchers.EDT) {
          runBlockingModal(ModalTaskOwner.guess(), "") {
            42
          }
        }
      }
    }
    assertEquals(42, result)
  }

  @Test
  fun `current modality state is set properly`(): Unit = timeoutRunBlocking {
    runBlockingModalTest {
      blockingContextTest()
    }
    progressManagerTest {
      blockingContextTest()
    }
  }

  private suspend fun blockingContextTest() {
    val contextModality = requireNotNull(currentCoroutineContext().contextModality())
    blockingContext {
      assertSame(contextModality, ModalityState.defaultModalityState())
      runBlockingCancellable {
        progressManagerTest {
          val nestedModality = currentCoroutineContext().contextModality()
          blockingContext {
            assertSame(nestedModality, ModalityState.defaultModalityState())
          }
        }
        runBlockingModalTest {
          val nestedModality = currentCoroutineContext().contextModality()
          blockingContext {
            assertSame(nestedModality, ModalityState.defaultModalityState())
          }
        }
      }
    }
  }

  private suspend fun runBlockingModalTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      runBlockingModal(ModalTaskOwner.guess(), "") {
        val modality = requireNotNull(currentCoroutineContext().contextModality())
        assertNotEquals(modality, ModalityState.NON_MODAL)
        assertSame(ModalityState.NON_MODAL, ModalityState.defaultModalityState())
        action()
      }
    }
  }

  private suspend fun progressManagerTest(action: suspend () -> Unit) {
    withContext(Dispatchers.EDT) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
        val modality = ModalityState.defaultModalityState()
        assertNotEquals(modality, ModalityState.NON_MODAL)
        runBlockingCancellable {
          assertSame(ModalityState.NON_MODAL, ModalityState.defaultModalityState())
          assertSame(modality, currentCoroutineContext().contextModality())
          action()
        }
      }, "", true, null)
    }
  }
}
