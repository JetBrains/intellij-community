// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
data class ExecutionInitiator(val id: String) {
  val contextElement: ExecutionInitiatorElement = ExecutionInitiatorElement(this)

  companion object {
    const val RPC_META_KEY: String = "ij.execution.initiator"

    val USER: ExecutionInitiator = ExecutionInitiator("USER")
    val MCP: ExecutionInitiator = ExecutionInitiator("MCP")

    fun currentOrNull(): ExecutionInitiator? =
      currentThreadContextOrNull()?.get(ExecutionInitiatorElement)?.initiator
  }
}

@ApiStatus.Internal
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class ExecutionInitiatorElement internal constructor(val initiator: ExecutionInitiator)
  : AbstractCoroutineContextElement(ExecutionInitiatorElement),
    CopyableThreadContextElement<Unit>,
    IntelliJContextElement,
    ExternalIntelliJContextElement {

  companion object : CoroutineContext.Key<ExecutionInitiatorElement>

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override fun copyForChild(): CopyableThreadContextElement<Unit> = this

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = overwritingElement

  override fun updateThreadContext(context: CoroutineContext): Unit = Unit

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit): Unit = Unit

  override fun toString(): String = "ExecutionInitiator(${initiator.id})"
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
@DelicateCoroutinesApi
@ApiStatus.Internal
object ExecutionInitiatorElementPrecursor :
  CopyableThreadContextElement<Unit>,
  CoroutineContext.Key<ExecutionInitiatorElementPrecursor>,
  IntelliJContextElement {

  override fun copyForChild(): CopyableThreadContextElement<Unit> {
    return currentThreadContext()[ExecutionInitiatorElement] ?: this
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
    if (overwritingElement[ExecutionInitiatorElement] != null) {
      return overwritingElement.minusKey(this)
    }
    val initiatorElement = currentThreadContext()[ExecutionInitiatorElement]
    if (initiatorElement != null) {
      return overwritingElement.minusKey(this) + initiatorElement
    }
    return overwritingElement
  }

  override fun updateThreadContext(context: CoroutineContext): Unit = Unit

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit): Unit = Unit

  override val key: CoroutineContext.Key<*>
    get() = this

  override fun toString(): String = "ExecutionInitiatorPrecursor"
}
