package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import inspections.cancellationCheckInLoops.Foo.doSomething
import java.util.function.Consumer

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
class Testing {

  @Suppress("UNUSED_PARAMETER")
  private fun withSuspendLambda(l: suspend () -> Any) { }

  @RequiresReadLock
  private fun foo1(array: Array<String>, list: List<String>, map: Map<String, String>, iterator: Iterator<String>, sequence: Sequence<String>) {
    array.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    array.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

    list.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    list.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

    sequence.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    sequence.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

    map.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { e -> doSomething() }
    map.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { k, v -> doSomething() }

    iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachRemaining</warning> { doSomething() }

    ContainerUtil.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">process</warning>(list) {
      true
    }
  }

  @RequiresReadLock
  fun foo2(array: Array<String>, list: List<String>, map: Map<String, String>, iterator: Iterator<String>, sequence: Sequence<String>) {
    withSuspendLambda {
      array.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
      array.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

      list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
      list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

      sequence.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
      sequence.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

      map.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { e -> doSomething() }
      map.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { k, v -> doSomething() }

      iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
      iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachRemaining</warning> { doSomething() }

      ContainerUtil.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">process</warning>(list) {
        true
      }
    }
  }

  @RequiresReadLock
  suspend fun foo3(array: Array<String>, list: List<String>, map: Map<String, String>, iterator: Iterator<String>, sequence: Sequence<String>) {
    array.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    array.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

    list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

    sequence.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    sequence.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }

    map.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { e -> doSomething() }
    map.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { k, v -> doSomething() }

    iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    iterator.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachRemaining</warning> { doSomething() }

    ContainerUtil.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">process</warning>(list) {
      true
    }
  }

  // no @RequiresReadLock
  private fun foo4(array: Array<String>) {
    array.forEach { doSomething() }
  }

  // no @RequiresReadLock
  fun foo5(list: List<String>) {
    withSuspendLambda {
      list.forEach { doSomething() }
    }
  }

  // no @RequiresReadLock
  suspend fun foo6(sequence: Sequence<String>) {
    sequence.forEachIndexed { index, s -> doSomething() }
  }

}
