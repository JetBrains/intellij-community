// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertConcurrent
import com.intellij.testFramework.assertConcurrentPromises
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.apache.log4j.Level
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("UsePropertyAccessSyntax")
class AsyncPromiseTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  fun done() {
    doHandlerTest(false)
  }

  @Test
  fun cancel() {
    val promise = AsyncPromise<Boolean>()
    assertThat(promise.isCancelled).isFalse()
    assertThat(promise.cancel(true)).isTrue()
    assertThat(promise.isCancelled).isTrue()
    assertThat(promise.cancel(true)).isFalse()
    assertThat(promise.isCancelled).isTrue()
    assertThat(promise.blockingGet(1)).isNull()
  }

  @Test
  fun rejected() {
    doHandlerTest(true)
  }

  @Test
  fun state() {
    val promise = AsyncPromise<String>()
    val count = AtomicInteger()
    val log = StringBuffer()

    class Incrementer(val descr:String) : ()->Promise<String> {
      override fun toString(): String {
        return descr
      }

      override fun invoke(): Promise<String> {
        return promise.onSuccess { count.incrementAndGet(); log.append("\n" + this + " " + System.identityHashCode(this)) }
      }
    }

    val setResultHandler: () -> Promise<String> = {
      promise.setResult("test")
      promise
    }

    val numThreads = 30
    val array = Array(numThreads) {
      if ((it and 1) == 0) Incrementer("handler $it") else setResultHandler
    }
    assertConcurrentPromises(*array)

    if (count.get() != (numThreads / 2)) {
      fail("count: "+count +" "+ log.toString()+"\n---Array:\n"+ Arrays.toString(array))
    }
    assertThat(count.get()).isEqualTo(numThreads / 2)
    assertThat(promise.get()).isEqualTo("test")

    Incrementer("extra").invoke()
    assertThat(count.get()).isEqualTo((numThreads / 2) + 1)
  }

  @Test
  fun blockingGet() {
    val promise = AsyncPromise<String>()
    assertConcurrent(
      { assertThat(promise.blockingGet(1000)).isEqualTo("test") },
      {
        Thread.sleep(100)
        promise.setResult("test")
      })
  }

  @Test
  fun `get from Future`() {
    val promise = AsyncPromise<String>()
    assertConcurrent(
      { assertThat(promise.get(1000, TimeUnit.MILLISECONDS)).isEqualTo("test") },
      {
        Thread.sleep(100)
        promise.setResult("test")
      })
  }

  @Test
  fun `ignore errors`() {
    val a = resolvedPromise("foo")
    val b = rejectedPromise<String>()
    assertThat(listOf(a, b).collectResults(ignoreErrors = true).blockingGet(100, TimeUnit.MILLISECONDS)).containsExactly("foo")
  }

  @Test
  fun blockingGet2() {
    val promise = AsyncPromise<String>()
    assertThatThrownBy { promise.blockingGet(10) }.isInstanceOf(TimeoutException::class.java)
  }

  private fun doHandlerTest(reject: Boolean) {
    val promise = AsyncPromise<String>()
    val count = AtomicInteger()

    val r = {
      if (reject) {
        promise.onError { count.incrementAndGet() }
      }
      else {
        promise.onSuccess { count.incrementAndGet() }
      }
    }

    val numThreads = 30
    assertConcurrent(*Array(numThreads) { r })

    if (reject) {
      promise.setError("test")
    }
    else {
      promise.setResult("test")
    }

    assertThat(count.get()).isEqualTo(numThreads)
    if (!reject) {
      assertThat(promise.get()).isEqualTo("test")
    }
    assertThat(promise.isDone).isTrue()
    assertThat(promise.isCancelled).isFalse()

    r()
    assertThat(count.get()).isEqualTo(numThreads + 1)
  }

  @Test
  fun `collectResults must return array with the same order`() {
    val promise0 = AsyncPromise<String>()
    val promise1 = AsyncPromise<String>()
    val f0 = JobScheduler.getScheduler().schedule({ promise0.setResult("0") }, 1, TimeUnit.SECONDS)
    val f1 = JobScheduler.getScheduler().schedule({ promise1.setResult("1") }, 1, TimeUnit.MILLISECONDS)
    val list = listOf(promise0, promise1)
    val results = list.collectResults()
    val l = results.blockingGet(1, TimeUnit.MINUTES)
    assertThat(l).containsExactly("0", "1")
    f0.get()
    f1.get()
  }

  @Test
  fun `collectResults must return array with the same order - ignore errors`() {
    val promiseList = listOf<AsyncPromise<String>>(AsyncPromise(), AsyncPromise(), AsyncPromise())
    val toExecute = listOf(
      JobScheduler.getScheduler().schedule({ promiseList[0].setResult("0") }, 5, TimeUnit.MILLISECONDS),
      JobScheduler.getScheduler().schedule({ promiseList[1].setError("boo") }, 1, TimeUnit.MILLISECONDS),
      JobScheduler.getScheduler().schedule({ promiseList[2].setResult("1") }, 2, TimeUnit.MILLISECONDS)
    )
    val results = promiseList.collectResults(ignoreErrors = true)
    val l = results.blockingGet(15, TimeUnit.SECONDS)
    assertThat(l).containsExactly("0", "1")
    toExecute.forEach { it.get() }
  }

  @Test
  fun `do not swallow exceptions`() {
    val promise = AsyncPromise<String>()
    val error = Error("boo")
    assertThatThrownBy {
      promise.setError(error)
    }
      .isInstanceOf(AssertionError::class.java)
      .hasCause(error)
  }

  @Test
  fun `do not swallow exceptions - do not log CancellationException`() {
    val promise = AsyncPromise<String>()
    promise.cancel()
    assertThat(promise.state).isEqualTo(Promise.State.REJECTED)
  }

  @Test
  fun `do not swallow exceptions - error in handler`() {
    val promise = AsyncPromise<String>()
    val promise2 = promise
      .onSuccess {
        throw java.lang.AssertionError("boo")
      }
    promise.setResult("foo")
    assertThat(promise.state).isEqualTo(Promise.State.SUCCEEDED)
    assertThat(promise2.state).isEqualTo(Promise.State.REJECTED)
  }

  @Test
  fun `do not swallow exceptions - error2 in handler`() {
    val oldFactory = Logger.getFactory()
    try {
      Logger.setFactory {
        object : Logger() {
          override fun warn(message: String?, t: Throwable?) {
          }

          override fun setLevel(level: Level?) {
          }

          override fun info(message: String?) {
          }

          override fun info(message: String?, t: Throwable?) {
          }

          override fun error(message: String?, t: Throwable?, vararg details: String?) {
          }

          override fun isDebugEnabled(): Boolean {
            return false
          }

          override fun debug(message: String?) {
          }

          override fun debug(t: Throwable?) {
          }

          override fun debug(message: String?, t: Throwable?) {
          }
        }
      }
      val promise = AsyncPromise<String>()
      val promise2 = promise
        .onSuccess {
          throw java.lang.AssertionError("boo")
        }
      promise.setResult("foo")
      assertThat(promise.state).isEqualTo(Promise.State.SUCCEEDED)
      assertThat(promise2.state).isEqualTo(Promise.State.REJECTED)
    }
    finally {
      Logger.setFactory(oldFactory)
    }
  }

  @Test
  fun `do not swallow exceptions - error3 in handler`() {
    val promise = AsyncPromise<String>()
    val promise2 = promise
      .onSuccess {
        throw ProcessCanceledException()
      }
    promise.setResult("foo")
    assertThat(promise.state).isEqualTo(Promise.State.SUCCEEDED)
    assertThat(promise2.state).isEqualTo(Promise.State.REJECTED)
  }

  @Test
  fun `do not swallow exceptions -  cancelled`() {
    val promise = AsyncPromise<String>()
    val promise2 = promise
      .onSuccess {
        throw ProcessCanceledException()
      }
    promise.cancel()
    assertThat(promise.state).isEqualTo(Promise.State.REJECTED)
    assertThat(promise2.state).isEqualTo(Promise.State.REJECTED)
  }

  @Test
  fun `do not swallow exceptions - error in success handler and no error in error handler`() {
    val promise = AsyncPromise<String>()
    var errorHandled = false
    val promise2 = promise
      .onSuccess {
        throw ProcessCanceledException()
      }
      .onError {
        errorHandled = true
      }
    promise.setResult("foo")
    assertThat(promise.state).isEqualTo(Promise.State.SUCCEEDED)
    assertThat(promise2.state).isEqualTo(Promise.State.REJECTED)
    assertThat(errorHandled).isTrue()
  }

  @Test
  fun `do not swallow exceptions - error in success handler and no error in error handler that added before success handler`() {
    val promise = AsyncPromise<String>()
    var errorHandled = false
    val promise2 = promise
      .onError {
        errorHandled = true
      }
      .onSuccess {
        throw ProcessCanceledException()
      }
    promise.setResult("foo")
    assertThat(promise.state).isEqualTo(Promise.State.SUCCEEDED)
    assertThat(promise2.state).isEqualTo(Promise.State.REJECTED)
    assertThat(errorHandled).isFalse()
  }

  // this case quite tested by other tests, but better to have special test
  @Test
  fun `do not swallow exceptions - error handler added`() {
    val promise = AsyncPromise<String>()
    val error = Error("boo")
    promise.onError {
      // ignore
    }
    promise.setError(error)
  }

  // this case quite tested by other tests, but better to have special test
  @Test
  fun `do not swallow exceptions - error handler added to nested`() {
    val promise = AsyncPromise<String>()
    val error = Error("boo")
    promise
      .onSuccess { }
      .onError {
        // ignore
      }
    promise.setError(error)
  }

  @Test
  fun `test onProcessed is called even for canceled promise`() {
    val called = AtomicBoolean();
    val promise = AsyncPromise<String>()
    promise.onProcessed {
      called.set(true)
    }
    promise.cancel()
    assertTrue(called.get())
  }

  @Test
  fun testExceptionInsideComputationIsLogged() {
    val loggedError = AtomicBoolean()
    LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
      override fun processError(message: String?, t: Throwable?, details: Array<out String>?, logger: org.apache.log4j.Logger) {
        loggedError.set(true)
      }
    })

    try {
      val promise = ReadAction.nonBlocking {
        throw UnsupportedOperationException()
      }
        .submit(AppExecutorUtil.getAppExecutorService())

      promise.onProcessed { }

      var cause: Throwable? = null
      try {
        promise.get(10, TimeUnit.SECONDS)
      }
      catch (e: Throwable) {
        cause = ExceptionUtil.getRootCause(e)
      }

      assertThat(cause).isInstanceOf(UnsupportedOperationException::class.java)
      assertThat(loggedError.get()).isTrue()
    }
    finally {
      LoggedErrorProcessor.restoreDefaultProcessor()
    }
  }
}