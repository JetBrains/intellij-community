// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.InternalCoroutineContextKey
import com.intellij.openapi.components.ComponentManager
import com.intellij.platform.util.coroutines.attachAsChildTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
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
) : AbstractCoroutineContextElement(ComponentManagerElementKey), IntelliJContextElement {

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override fun toString(): String = "ComponentManager(${componentManager.debugString()})"
}

private object ComponentManagerElementKey : CoroutineContext.Key<ComponentManagerElement>, InternalCoroutineContextKey<ComponentManagerElement>

/**
 * Runs [action] in an intersection scope of the current coroutine and the [container] scope:
 * - The action is canceled when the current coroutine is canceled
 * - The action is canceled when the container scope is canceled.
 * - The container awaits completion of the action.
 *
 * The [container] becomes the [context container][contextComponentManager] inside [action],
 * which means [instance] will delegate requests to [container].
 *
 * @see withContext
 * @see attachAsChildTo
 */
@Internal
@TestOnly // Originally implemented to bind the test coroutine to the container. This can be lifted later
suspend fun <T> withContainerContext(container: ComponentManager, action: suspend CoroutineScope.() -> T): T {
  val containerScope = (container as ComponentManagerImpl).getCoroutineScope()

  /**
   * Inherit container context, this enables [instance] to work inside [action].
   * Don't inherit container [Job] because it will replace the current coroutine Job,
   * making the current coroutine effectively non-cancellable.
   */
  val context = containerScope.coroutineContext
    .minusKey(Job)
  return withContext(context) {
    attachAsChildTo(containerScope)
    action()
  }
}
