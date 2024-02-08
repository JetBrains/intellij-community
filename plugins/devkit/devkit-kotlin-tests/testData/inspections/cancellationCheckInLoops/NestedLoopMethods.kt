package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import inspections.cancellationCheckInLoops.Foo.doSomething
import java.util.function.Consumer

@Suppress("UNUSED_PARAMETER", "UNUSED_ANONYMOUS_PARAMETER")
class Testing {

  @Suppress("UNUSED_PARAMETER")
  private fun withSuspendLambda(l: suspend () -> Any) { }

  @RequiresReadLock
  private fun foo1(array: Array<String>, list: List<String>, map: Map<String, String>, iterator: Iterator<String>, sequence: Sequence<String>) {
    array.forEach {
      list.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    }

    for (i in 1..10) {
      list.<warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }
    }

    sequence.forEach {
      <warning descr="Cancellation check 'com.intellij.openapi.progress.ProgressManager.checkCanceled' should be the first statement in a loop body">for</warning> (i in 1..10) {
        doSomething()
      }
    }
  }
  @RequiresReadLock
  fun foo2(array: Array<String>, list: List<String>, map: Map<String, String>, iterator: Iterator<String>, sequence: Sequence<String>) {
    withSuspendLambda {
      array.forEach {
        list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
      }

      for (i in 1..10) {
        list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }
      }

      sequence.forEach {
        <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">for</warning> (i in 1..10) {
          doSomething()
        }
      }
    }
  }

  @RequiresReadLock
  suspend fun foo3(array: Array<String>, list: List<String>, map: Map<String, String>, iterator: Iterator<String>, sequence: Sequence<String>) {
    array.forEach {
      list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEach</warning> { doSomething() }
    }

    for (i in 1..10) {
      list.<warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">forEachIndexed</warning> { index, s -> doSomething() }
    }

    sequence.forEach {
      <warning descr="Cancellation check 'com.intellij.openapi.progress.checkCancelled' should be the first statement in a loop body">for</warning> (i in 1..10) {
        doSomething()
      }
    }
  }
}
