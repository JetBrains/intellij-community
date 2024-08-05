// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * An object that is attached to an asynchronous computation.
 * This interface is the core actor in Context Propagation.
 *
 * When some process initiates an asynchronous computation (usually by submitting something to a task queue),
 * some data attached to the computation should be logically transferred from the process to the computation.
 * By implementing this interface, the data objects can react to the process of passing.
 *
 * ## Example
 * Consider the following snippet:
 * ```kotlin
 * withContext(myIntelliJElement) {         // suspending code
 *   platformScheduler.queueAsyncActivity { // non-suspending scheduling
 *     runSomething()                       // some computation, maybe also asynchronous
 *   }
 * }
 * ```
 *
 * `myIntelliJElement` behaves here in the following manner:
 *
 * ```kotlin
 * withContext(myIntelliJElement) {
 *   val initialContext = currentThreadContext()
 *   // the creation of a child context happens during the queueing
 *   val childElement = myIntelliJElement.produceChildElement(initialContext, ...)
 *   platformScheduler.queueAsyncActivity {
 *     // the execution happens on some other thread
 *     installThreadContext(initialContext + childElement) {
 *       // before the execution of a scheduled runnable,
 *       // the created element performs computations
 *       try {
 *         runSomething()
 *       } finally {
 *         // after the execution of a scheduled runnable,
 *         // the created element performs cleanup
 *         childElement.afterChildCompleted(currentThreadContext())
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * ## Structured propagation
 *
 * Sometimes it is known that the parent process lives strictly longer than the child computation.
 * If this is the case, then we have _structured children_.
 *
 * Example:
 * ```kotlin
 * fun foo() {
 *   invokeAndWait {
 *     // `foo` does not exit until `invokeAndWait` finishes
 *   }
 * }
 * ```
 * It can affect the behavior of context elements: if some activity within children relies on the termination of the parent process,
 * then the children can use the information that the parent never completes before they do.
 *
 * It can be useful in the following scenario:
 * ```kotlin
 * fun foo() {
 *   mutex.lock()
 *   scheduler.queue {
 *     mutex.lock() // <- will cause a deadlock if `queue` spawns a structured child
 *   }
 *   mutex.unlock()
 * }
 * ```
 *
 * More examples when structured computation takes place:
 *
 * ```kotlin
 * application.invokeAndWait {
 *   // parks the calling thread, so it is naturally structured
 * }
 *
 * blockingContextScope {
 *   // transforms all unstructured computations here into structured
 *   // with respect to the calling coroutine
 * }
 * ```
 *
 * ## Similar concepts
 * [IntelliJContextElement] is very similar to [kotlinx.coroutines.ThreadContextElement] of kotlinx coroutines.
 * The difference is that [kotlinx.coroutines.ThreadContextElement] invokes its action on every dispatch of a coroutine,
 * while [IntelliJContextElement] performs actions on "dispatch" of IntelliJ-specific asynchronous computations.
 *
 */
@ApiStatus.Experimental
interface IntelliJContextElement : CoroutineContext.Element {
  /**
   * Called when _scheduling_ of a child computation is requested.
   *
   * @param parentContext The context of a computation that requests the scheduling
   * @param isStructured indicates whether the spawned computation's lifetime is strictly smaller than the requesting computation's one
   * (see the section about structured propagation).
   * @return An element that should be added to the context of the child computation, or `null` if nothing should be added.
   */
  fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement? {
    return if (isStructured) this else null
  }

  /**
   * Called when the child computation ends its execution.
   *
   * @param context the context of the executing computation
   */
  fun afterChildCompleted(context: CoroutineContext) {}
}