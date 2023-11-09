// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.impl.assertNotReferenced
import com.intellij.openapi.application.impl.assertReferenced
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class IntersectionScopeTest {

  @Test
  fun `attach orphan scope`() {
    val parent = CoroutineScope(SupervisorJob())
    assertOrphan(parent)
    assertNoChildren(parent)

    val child = CoroutineScope(SupervisorJob())
    assertOrphan(child)
    assertNoChildren(child)

    child.attachAsChildTo(parent)
    assertOrphan(child)
    assertNoChildren(child)
    assertOrphan(parent)
    Assertions.assertEquals(1, parent.job().children.toList().size)
  }

  @Test
  fun `attach scope with parent`() {
    val trueParent = CoroutineScope(SupervisorJob())
    assertOrphan(trueParent)
    assertNoChildren(trueParent)

    val child = trueParent.childScope()
    assertParentChild(expectedParent = trueParent, expectedChild = child)
    assertNoChildren(child)

    val parent = CoroutineScope(SupervisorJob())
    assertOrphan(parent)
    assertNoChildren(parent)

    child.attachAsChildTo(parent)
    assertOrphan(trueParent)
    assertParentChild(expectedParent = trueParent, expectedChild = child)
    assertNoChildren(child)
    assertOrphan(parent)
    Assertions.assertEquals(1, parent.job().children.toList().size)
  }

  @Test
  fun `parent awaits child completion`() {
    val parentJob: CompletableJob = SupervisorJob()
    val childJob: CompletableJob = SupervisorJob()
    val child = CoroutineScope(childJob)
    child.attachAsChildTo(CoroutineScope(parentJob))

    val cancelled = Semaphore(1, 1)
    val canFinish = Semaphore(1, 1)
    child.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          cancelled.release()
          canFinish.acquire()
        }
      }
    }
    Assertions.assertFalse(childJob.isCancelled)
    Assertions.assertFalse(parentJob.isCancelled)

    parentJob.cancel()
    timeoutRunBlocking {
      cancelled.acquire()
      Assertions.assertTrue(childJob.isCancelled)
      Assertions.assertTrue(parentJob.isCancelled)
      Assertions.assertFalse(childJob.isCompleted)
      Assertions.assertFalse(parentJob.isCompleted)

      canFinish.release()
      parentJob.join()
      Assertions.assertTrue(childJob.isCompleted)
      Assertions.assertTrue(parentJob.isCompleted)
    }
  }

  @Test
  fun `parent cancellation cancels child`() {
    val parentJob: CompletableJob = SupervisorJob()
    val childJob: CompletableJob = SupervisorJob()
    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertFalse(childJob.isCancelled)

    parentJob.cancel()
    Assertions.assertTrue(parentJob.isCancelled)
    Assertions.assertTrue(childJob.isCancelled)
  }

  @Test
  fun `parent failure cancels child`() {
    val parentJob: CompletableJob = SupervisorJob()
    val childJob: CompletableJob = SupervisorJob()
    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertFalse(childJob.isCancelled)

    val t: Throwable = object : Throwable() {}
    parentJob.completeExceptionally(t)
    Assertions.assertTrue(parentJob.isCancelled)
    Assertions.assertTrue(childJob.isCancelled)
    @OptIn(InternalCoroutinesApi::class)
    (Assertions.assertSame(t, childJob.getCancellationException().cause))
  }

  @Test
  fun `child cancellation does not cancel parent`() {
    val parentJob: CompletableJob = Job()
    val childJob: CompletableJob = Job()
    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertFalse(childJob.isCancelled)

    childJob.cancel()
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertTrue(childJob.isCancelled)
  }

  @Test
  fun `child failure cancels parent`() {
    val parentJob: CompletableJob = Job()
    val childJob: CompletableJob = Job()
    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertFalse(childJob.isCancelled)

    val t: Throwable = object : Throwable() {}
    childJob.completeExceptionally(t)
    Assertions.assertTrue(parentJob.isCancelled) {
      parentJob.toString()
    }
    Assertions.assertTrue(childJob.isCancelled)
  }

  @Test
  fun `child failure does not cancel supervisor parent`() {
    val parentJob: CompletableJob = SupervisorJob()
    val childJob: CompletableJob = Job()
    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertFalse(childJob.isCancelled)

    val t: Throwable = object : Throwable() {}
    childJob.completeExceptionally(t)
    Assertions.assertFalse(parentJob.isCancelled)
    Assertions.assertTrue(childJob.isCancelled)
  }

  @Test
  fun `attach to cancelled parent cancels child`() {
    val parentJob: CompletableJob = SupervisorJob()
    parentJob.cancel()
    Assertions.assertTrue(parentJob.isCancelled)

    val childJob: CompletableJob = SupervisorJob()
    Assertions.assertFalse(childJob.isCancelled)

    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    Assertions.assertTrue(childJob.isCancelled)
  }

  @Test
  fun `completed child does not leak through parent`(): Unit = timeoutRunBlocking {
    val parentJob = Job()
    val childJob = Job()
    CoroutineScope(childJob).attachAsChildTo(CoroutineScope(parentJob))
    assertReferenced(parentJob, childJob)
    val childHandleJob = parentJob.children.single()
    childJob.cancel()
    childHandleJob.join()
    assertNotReferenced(parentJob, childJob)
  }
}

private fun assertOrphan(scope: CoroutineScope) {
  val actualParent = scope.parentJob()
  Assertions.assertNull(actualParent) {
    "Actual parent: $actualParent"
  }
}

private fun assertNoChildren(scope: CoroutineScope) {
  val children = scope.job().children.toList()
  Assertions.assertTrue(children.isEmpty()) {
    "Actual children: $children"
  }
}

private fun assertParentChild(expectedParent: CoroutineScope, expectedChild: CoroutineScope) {
  assertParent(expectedParent, expectedChild)
  assertChild(expectedChild, expectedParent)
}

private fun assertParent(expectedParent: CoroutineScope, child: CoroutineScope) {
  val actualParent = child.parentJob()
  Assertions.assertSame(expectedParent.job(), actualParent) {
    "Actual parent: $actualParent"
  }
}

private fun assertChild(expectedChild: CoroutineScope, parent: CoroutineScope) {
  val children = parent.job().children.toList()
  Assertions.assertTrue(children.contains(expectedChild.job())) {
    "Actual children: $children"
  }
}

private fun CoroutineScope.job(): Job {
  return coroutineContext.job
}

private fun CoroutineScope.parentJob(): Job? {
  return job().parent()
}

internal fun Job.parent(): Job? {
  @Suppress("DEPRECATION_ERROR", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
  @OptIn(InternalCoroutinesApi::class)
  return (this as JobSupport).parentHandle?.parent
}
