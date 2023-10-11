// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.components.ComponentManager
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Experimental
suspend inline fun <reified T : Any> instance(): T {
  return instance(T::class.java)
}

@Experimental
suspend fun <T : Any> instance(keyClass: Class<T>): T {
  val ctx = currentCoroutineContext()
  val manager = ctx.contextComponentManager() as ComponentManagerImpl
  return manager.getServiceAsync(keyClass)
}

private fun CoroutineContext.contextComponentManager(): ComponentManager {
  return checkNotNull(contextComponentManagerOrNull()) {
    "Coroutine is not a child of a service scope"
  }
}

private fun CoroutineContext.contextComponentManagerOrNull(): ComponentManager? {
  return get(ComponentManagerElementKey)?.componentManager
}

internal fun ComponentManagerImpl.asContextElement(): CoroutineContext.Element {
  return ComponentManagerElement(this)
}

private class ComponentManagerElement(
  val componentManager: ComponentManagerImpl,
) : AbstractCoroutineContextElement(ComponentManagerElementKey) {
  override fun toString(): String = "ComponentManager(${componentManager.debugString()})"
}

private object ComponentManagerElementKey : CoroutineContext.Key<ComponentManagerElement>
