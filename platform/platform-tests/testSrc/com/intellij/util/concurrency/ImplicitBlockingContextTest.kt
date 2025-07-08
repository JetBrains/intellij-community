// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import io.kotest.mpp.atomics.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.coroutines.*

@OptIn(InternalCoroutinesApi::class)
@TestApplication
class ImplicitBlockingContextTest {

  @Test
  fun noThreadContextByDefault() {
    assertNull(currentThreadContextOrNull())
    assertNull(IntellijCoroutines.currentThreadCoroutineContext())
    assertEquals(EmptyCoroutineContext, currentThreadContext())
  }

  @Test
  fun basicRunBlocking(): Unit = runBlocking {
    assertContextsEqual()
  }

  class E : AbstractCoroutineContextElement(E), IntelliJContextElement {
    companion object Key : CoroutineContext.Key<E>

    override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
  }

  class F : AbstractCoroutineContextElement(F), IntelliJContextElement {
    companion object Key : CoroutineContext.Key<F>
  }

  @Test
  fun basicWithContext(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      assertContextsEqual()
    }
  }

  @Test
  fun invokeLater(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      ApplicationManager.getApplication().invokeLater {
        assertContextRemainsOnFreeThread()
      }
    }
  }

  @Test
  fun executeOnPooledThread(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      ApplicationManager.getApplication().executeOnPooledThread {
        assertContextRemainsOnFreeThread()
      }
    }
  }

  @Test
  fun runBlockingCancellableDoesPreserveContext(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      val currentContext = coroutineContext
      runBlockingCancellable {
        // the equality here holds up to skeleton, since Job and CoroutineId would be different
        assertEquals(coroutineContext[E], currentContext[E])
      }
    }
  }


  @Test
  fun runBlockingDoesNotPreserveContext(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      val currentContext = coroutineContext
      runBlocking {
        assertNotEquals(coroutineContext[E], currentContext[E])
      }
    }
  }

  @Test
  fun installThreadContextTakesPriority(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      val context = coroutineContext
      val newContext = currentThreadContext() + F()
      installThreadContext(newContext) {
        assertEquals(IntellijCoroutines.currentThreadCoroutineContext(), context)
        assertNotEquals(context, currentThreadContext())
        assertEquals(newContext, currentThreadContext())
      }
    }
  }

  @Test
  fun resetThreadContextTakesPriority(): Unit = runBlockingWithCatchingExceptions {
    withContext(E()) {
      val context = coroutineContext
      resetThreadContext {
        assertEquals(IntellijCoroutines.currentThreadCoroutineContext(), context)
        assertNull(currentThreadContextOrNull())
        assertEquals(EmptyCoroutineContext, currentThreadContext())
      }
    }
  }


  fun runBlockingWithCatchingExceptions(action: suspend CoroutineScope.(CoroutineExceptionHandler) -> Unit) {
    val exceptionRef = AtomicReference<Throwable?>(null)
    val handler = CoroutineExceptionHandler { _, throwable ->
      exceptionRef.value = throwable
    }
    timeoutRunBlocking {
      withContext(handler) {
        action(handler)
      }
    }
    val exception = exceptionRef.value
    if (exception != null) {
      throw exception
    }
  }

  @Test
  fun undispatchedCoroutine(): Unit = runBlockingWithCatchingExceptions { handler ->
    val e = E()
    val f = F()
    fun `has f, not e`() {
      assertNull(currentThreadContext()[E])
      assertEquals(f, currentThreadContext()[F])
    }

    fun `has e, not f`() {
      assertNull(currentThreadContext()[F])
      assertEquals(e, currentThreadContext()[E])
    }

    installThreadContext(currentThreadContext() + e) {
      `has e, not f`()
      @Suppress("SSBasedInspection")
      CoroutineScope(f + handler).launch(start = CoroutineStart.UNDISPATCHED) {
        `has f, not e`()
        installThreadContext(e + handler) {
          `has e, not f`()
        }
        `has f, not e`()
        yield()
        `has f, not e`()
      }
      `has e, not f`()
    }
  }

  private suspend fun assertContextsEqual() {
    val context = coroutineContext
    assertEquals(context.minusKey(ContinuationInterceptor), currentThreadContext())
  }

  private fun assertContextRemainsOnFreeThread() {
    assertNull(IntellijCoroutines.currentThreadCoroutineContext())
    val set = currentThreadContext().fold(HashSet<CoroutineContext.Key<*>>(), { list, elem -> list.apply { add(elem.key) } })
    assertTrue(set.contains(E))
  }
}