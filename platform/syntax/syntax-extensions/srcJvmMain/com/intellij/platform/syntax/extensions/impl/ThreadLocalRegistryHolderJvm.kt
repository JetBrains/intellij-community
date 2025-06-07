// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.extensions.ExtensionSupport
import fleet.util.multiplatform.Actual

internal object ThreadLocalRegistryHolderJvm : RegistryHolder {
  private val threadLocal = ThreadLocal<ExtensionSupport>()

  override val registry: ExtensionSupport?
    get() = threadLocal.get()

  override fun installRegistry(registry: ExtensionSupport) {
    threadLocal.set(registry)
  }
}

/**
 * @see instantiateThreadLocalRegistry
 */
@Actual("instantiateThreadLocalRegistry")
internal fun instantiateThreadLocalRegistryJvm(): RegistryHolder =
  ThreadLocalRegistryHolderJvm