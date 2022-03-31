// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl

import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.deft.Obj
import org.jetbrains.deft.ObjId
import org.jetbrains.deft.Root
import org.jetbrains.deft.RootImpl
import org.jetbrains.deft.collections.Ref
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

open class SnapshotView(
  val graph: ObjGraph,
  //val workspaceHandle: WorkspaceHandle? = null,
  override val complete: Boolean = true
) : View {
  override val version: Int
    get() = graph.version

  override val root: RootImpl
    get() = load(ObjId(1))

  // ACTIVE
  // waitingHoldsForClose: AtomicReference<CompletableDeferred<Unit>>
  // null — closed
  private var state = AtomicReference<Any?>(ACTIVE)

  val active: Boolean
    get() = state.get() == ACTIVE

  @Suppress("UNCHECKED_CAST")
  private val waitingHoldsForClose: CompletableDeferred<Unit>?
    get() = state.get() as? CompletableDeferred<Unit>

  private val holders = AtomicInteger()

  val viewId: Int = graph.dataViewId

  init {
    check(graph.owner == null)
    graph.owner = this
  }

  fun checkCancelled() {
    if (!active) throw ReadCancelled()
  }

  fun <T> load(id: ObjId<T>): T {
    checkCancelled()
    return graph.getOrLoad(id)
  }

  @Deprecated("")
  override fun <T : Obj> Ref<T>.resolve(): T? =
    get(graph) as T

  fun tryAllocateHold(): Boolean {
    holders.incrementAndGet()
    if (active) return true
    else {
      releaseHoldUnchecked()
      return false
    }
  }

  fun releaseHold() {
    check(active)
    releaseHoldUnchecked()
  }

  private fun releaseHoldUnchecked() {
    if (holders.decrementAndGet() == 0) {
      waitingHoldsForClose?.complete(Unit)
    }
  }

  @OptIn(ExperimentalContracts::class)
  inline fun hold(actions: () -> Unit) {
    contract {
      callsInPlace(actions, InvocationKind.EXACTLY_ONCE)
    }

    check(tryAllocateHold())
    try {
      actions()
    } finally {
      releaseHold()
    }
  }

  suspend fun waitHoldsAndClose() {
    val waitingHoldsForClose = CompletableDeferred<Unit>()
    check(state.getAndSet(waitingHoldsForClose) == ACTIVE)
    if (holders.get() != 0) {
      waitingHoldsForClose.await()
    }
    close()
  }

  override fun close() {
    //coroutineScope.launch { data.releaseSnapshotView(viewId) }
    deactivate()
  }

  private fun deactivate() {
    check(state.getAndSet(null) != null)
    graph.owner = null
  }

  fun cancel() {
    close()
  }

  override fun toString(): String = graph.toString()
}

private val ACTIVE = Symbol("ACTIVE")

internal class Symbol(val symbol: String) {
  override fun toString(): String = "<$symbol>"

  @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
  inline fun <T> unbox(value: Any?): T = if (value === this) null as T else value as T
}

interface View : AutoCloseable {
  val version: Int
  val complete: Boolean

  val root: Root

  //@Deprecated("")
  //val names: ObjNames
  //  get() = error("names are not available anymore")

  @Deprecated("")
  fun <T : Obj> Ref<T>.resolve(): T?
}

public final class ReadCancelled public constructor() : Throwable() {
}