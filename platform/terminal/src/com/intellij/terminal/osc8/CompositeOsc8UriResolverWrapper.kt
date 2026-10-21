// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the [CompositeOsc8UriResolver] built from the [Osc8UriResolverProvider] extensions
 * for a [Project] and rebuilds it on the extensions change.
 *
 * The implementation is thread-safe.
 */
@ApiStatus.Internal
class CompositeOsc8UriResolverWrapper(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  @Volatile
  private var lazy: Lazy<CompositeOsc8UriResolver> = createCompositeResolverLazy()

  private val updateListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  init {
    Osc8UriResolverProvider.EP_NAME.addChangeListener(coroutineScope) {
      lazy = createCompositeResolverLazy()
      fireUpdate()
    }
  }

  /**
   * Adds a [listener] that is called after the extensions change, so that the next [getCompositeResolver] call
   * returns a new resolver. The listener is called on the thread that changes the extensions.
   */
  fun addUpdateListener(listener: () -> Unit) {
    updateListeners.add(listener)
  }

  private fun fireUpdate() {
    for (listener in updateListeners) {
      listener()
    }
  }

  /**
   * Returns the composite resolver.
   */
  fun getCompositeResolver(): CompositeOsc8UriResolver = lazy.value

  private fun createCompositeResolverLazy(): Lazy<CompositeOsc8UriResolver> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    val resolvers = ArrayList<Osc8UriResolver>()
    Osc8UriResolverProvider.EP_NAME.forEachExtensionSafe {
      resolvers.addAll(it.getResolvers(project))
    }
    CompositeOsc8UriResolver(resolvers)
  }
}
