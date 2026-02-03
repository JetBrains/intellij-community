// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class CoroutineDumpTest {
  companion object {
    // for `testDeduplicationPreservesAddressOfBlockingCoroutines`
    //init {
    //  System.setProperty("kotlinx.coroutines.debug", "off")
    //}

    @JvmStatic
    @BeforeAll
    fun enableDumps() {
      enableCoroutineDump()
    }
  }

  @OptIn(InternalCoroutinesApi::class)
  @Suppress("SSBasedInspection", "DEPRECATION_ERROR")
  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  fun testRecursiveJobsDump() {
    val projectScope = CoroutineScope(CoroutineName("Project"))
    val pluginScope = CoroutineScope(CoroutineName("Plugin"))
    val activity = projectScope.childScope("Project Activity")
    pluginScope.coroutineContext[Job]!!.attachChild(activity.coroutineContext[Job]!! as ChildJob)
    // e.g. bug here
    activity.coroutineContext[Job]!!.attachChild(pluginScope.coroutineContext[Job]!! as ChildJob)
    assertEquals("""
- JobImpl{Active}
	- "Project Activity":supervisor:ChildScope{Active}
		- JobImpl{Active}
			- CIRCULAR REFERENCE: "Project Activity":supervisor:ChildScope{Active}
""", dumpCoroutines(projectScope, true, true))
    assertEquals("""
- JobImpl{Active}
	- "Project Activity":supervisor:ChildScope{Active}
		- CIRCULAR REFERENCE: JobImpl{Active}
""", dumpCoroutines(pluginScope, true, true))
    projectScope.cancel()
    pluginScope.cancel()
  }

  // IJPL-159382
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  @Test
  fun testDeduplicationPreservesAddressOfBlockingCoroutines() {
    val b1 = CompletableDeferred<Unit>()
    val blockingCoroutine = AtomicReference<Job?>()
    val b2 = CompletableDeferred<Unit>()
    val t = thread {
      runBlocking {
        blockingCoroutine.set(coroutineContext[Job])
        // TODO currently CoroutineId prevents deduplication in tests,
        //  uncomment three following parts that check that deduplication still works if CoroutineId is no longer present
        //val awaitingCoros = ArrayList<Continuation<Unit>>()
        //repeat(10) {
        //  launch(CoroutineName("test coro")) {
        //    suspendCancellableCoroutine { // only one thread, so no concurrency issues
        //      awaitingCoros.add(it)
        //    }
        //  }
        //}
        //while (awaitingCoros.size != 10) {
        //  yield()
        //}
        b1.complete(Unit)
        b2.await()
        //awaitingCoros.forEach { it.resumeWith(Result.success(Unit)) }
      }
    }
    runBlocking {
      b1.await()
    }
    try {
      while (LockSupport.getBlocker(t) == null) { // wait until runBlocking parks
        Thread.yield()
      }
      assertEquals(blockingCoroutine.get(), LockSupport.getBlocker(t))
      val dumpWithDeduplication = dumpCoroutines(blockingCoroutine.get()!! as CoroutineScope, stripDump = false, deduplicateTrees = true)!!
      assert(dumpWithDeduplication.contains(blockingCoroutine.get()!!.toString())) { dumpWithDeduplication }
      assert(blockingCoroutine.get()!!.toString().contains("}@")) { dumpWithDeduplication }
      //assert(dumpWithDeduplication.contains("[x10 of]")) { dumpWithDeduplication }
    } finally {
      b2.complete(Unit)
      t.join()
    }
  }

  private class AnElement : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AnElement>
  }

  @Test
  fun `different instances of the same class with base toString are deduplicated`() = timeoutRunBlocking {
    val job = Job()
    repeat(10) {
      launch(AnElement()) {
        job.join()
      }
    }
    try {
      val dumpWithDeduplication = dumpCoroutines(this, stripDump = false, deduplicateTrees = true)!!
      // because in debug mode, there is CoroutineId which appears in the context and in coroutine names
      val dumpWithReplacedNumbers = dumpWithDeduplication.replace(Regex("[0-9]+"), "<NUM>").lines()
      val targetLine = dumpWithReplacedNumbers.find { it.contains("AnElement") }!!
      // if it was not for CoroutineId, this dump would be deduplicated
      // here we check that apart from these numbers, nothing blocks the deduplication
      assert(dumpWithReplacedNumbers.count { it == targetLine } == 10)
    }
    finally {
      job.complete()
    }
  }
}