// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.extensions.ExtensionRegistry
import com.intellij.platform.syntax.extensions.ExtensionSupport
import com.intellij.platform.syntax.extensions.StaticExtensionSupport
import fleet.util.multiplatform.linkToActual
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal val registry: ExtensionSupport
  get() {
    return staticRegistryHolder.registry       // usually, IntelliJ support
           ?: threadLocalRegistry.registry     // local thread context
           ?: instantiateExtensionRegistry()   // callback
  }

internal val staticRegistryHolder: RegistryHolder by lazy {
  val support = instantiateExtensionRegistry().takeIf { it is StaticExtensionSupport }
  object : RegistryHolder {
    override val registry: ExtensionSupport?
      get() = support

    override fun installRegistry(registry: ExtensionSupport): Unit =
      throw UnsupportedOperationException()
  }
}

/**
 * Tries to find [com.intellij.platform.syntax.psi.IntelliJExtensionSupport] in the class path.
 * If it succeeds, it means we are running in IntelliJ, and we must use IJ plugin model as the [ExtensionSupport] backend.
 * Otherwise, returning [ExtensionRegistryImpl].
 *
 * @See instantiateExtensionRegistryJvm
 * @See instantiateExtensionRegistryWasmJs
 */
internal fun instantiateExtensionRegistry(): ExtensionSupport = linkToActual()

internal val threadLocalRegistry: RegistryHolder = instantiateThreadLocalRegistry()

@OptIn(ExperimentalContracts::class)
internal fun <T> performWithExtensionSupportImpl(support: ExtensionSupport, action: (ExtensionSupport) -> T): T {
  contract {
    callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
  }

  val oldRegistry = threadLocalRegistry.registry
  try {
    threadLocalRegistry.installRegistry(support)
    return action(support)
  }
  finally {
    if (oldRegistry != null) {
      threadLocalRegistry.installRegistry(oldRegistry)
    }
  }
}

internal interface RegistryHolder {
  val registry: ExtensionSupport?
  fun installRegistry(registry: ExtensionSupport)
}

/**
 * @see instantiateThreadLocalRegistryJvm
 * @see instantiateThreadLocalRegistryWasmJs
 */
internal fun instantiateThreadLocalRegistry(): RegistryHolder = linkToActual()

internal fun buildExtensionSupportImpl(block: ExtensionRegistry.() -> Unit): ExtensionSupport {
  val registry = instantiateExtensionRegistry() as? ExtensionRegistry ?: error("Failed to create ExtensionRegistry")
  registry.block()
  return registry
}
