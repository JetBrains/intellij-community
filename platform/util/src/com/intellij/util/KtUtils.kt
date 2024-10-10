// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinUtils")

package com.intellij.util

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

@OptIn(ExperimentalContracts::class)
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
inline fun <reified T : Any> Any?.asSafely(): @kotlin.internal.NoInfer T? {
  contract {
    returnsNotNull() implies (this@asSafely is T)
  }
  return this as? T
}

@OptIn(ExperimentalContracts::class)
inline fun <T> runIf(condition: Boolean, block: () -> T): T? {
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return if (condition) block() else null
}

@ApiStatus.ScheduledForRemoval
@Deprecated("""
  Unfortunately, this function provokes cryptic code, please do not use it.
  
  Consider these options instead:
  * val result = my().chain() ?: return null
  * val result = my().chain() ?: run { log.warn("null result!"); return null }
  * val result = my().chain(); if (result == null) { log.warn("null result!") } else { useNotNull(result) }  
  * if (my().chain() == null) { log.warn("null result!") }
""", level = DeprecationLevel.ERROR)
inline fun <T : Any> T?.alsoIfNull(block: () -> Unit): T? {
  if (this == null) {
    block()
  }
  return this
}

@OptIn(ExperimentalContracts::class)
inline fun <T> T.applyIf(condition: Boolean, body: T.() -> T): T {
  contract {
    callsInPlace(body, InvocationKind.AT_MOST_ONCE)
  }
  return if (condition) body() else this
}

typealias AsyncSupplier<T> = suspend () -> T

operator fun <V> AtomicReference<V>.getValue(thisRef: Any?, property: KProperty<*>): V = get()

operator fun <V> AtomicReference<V>.setValue(thisRef: Any?, property: KProperty<*>, value: V): Unit = set(value)

@OptIn(ExperimentalContracts::class)
inline fun <T1 : AutoCloseable?, T2 : AutoCloseable?, R>
  Pair<() -> T1, () -> T2>.use(block: (T1, T2) -> R): R {
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return first.invoke().use { o1 -> second.invoke().use { o2 -> block(o1, o2) } }
}

@OptIn(ExperimentalContracts::class)
inline fun <T1 : AutoCloseable?, T2 : AutoCloseable?, T3 : AutoCloseable, R>
  Triple<() -> T1, () -> T2, () -> T3>.use(block: (T1, T2, T3) -> R): R {
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return first.invoke().use { o1 -> second.invoke().use { o2 -> third.invoke().use { o3 -> block(o1, o2, o3) } } }
}

/**
 * @return a new sequence that contains the elements from the original sequence
 * up to and including the element for which the [predicate] returns false.
 */
fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> {
  return TakeWhileInclusiveSequence(this, predicate)
}

internal class TakeWhileInclusiveSequence<T>(
  private val sequence: Sequence<T>,
  private val predicate: (T) -> Boolean
) : Sequence<T> {

  enum class NextState {
    UNKNOWN,
    DONE,
    CONTINUE
  }

  override fun iterator(): Iterator<T> = object : Iterator<T> {
    val iterator = sequence.iterator()
    var currentState = NextState.UNKNOWN
    var nextState: NextState = NextState.UNKNOWN
    var nextItem: T? = null

    private fun calcNext() {
      if (iterator.hasNext()) {
        val item = iterator.next()
        nextItem = item
        currentState = NextState.CONTINUE
        nextState = if (predicate(item)) NextState.UNKNOWN else NextState.DONE
      }
      else {
        currentState = NextState.DONE
        nextState = NextState.DONE
      }
    }

    override fun next(): T {
      if (!hasNext()) throw NoSuchElementException()
      @Suppress("UNCHECKED_CAST")
      val result = nextItem as T

      // Clean next to avoid keeping reference on yielded instance
      nextItem = null
      currentState = nextState
      return result
    }

    override fun hasNext(): Boolean {
      if (currentState == NextState.UNKNOWN)
        calcNext() // will change nextState
      return currentState == NextState.CONTINUE
    }
  }
}
