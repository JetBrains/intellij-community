// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

internal object ThreadLocalRegistryHolderNative : com.intellij.platform.syntax.extensions.impl.RegistryHolder {
  private var threadLocal: com.intellij.platform.syntax.extensions.ExtensionSupport? = null

  override val registry: com.intellij.platform.syntax.extensions.ExtensionSupport?
    get() = threadLocal

  override fun installRegistry(registry: com.intellij.platform.syntax.extensions.ExtensionSupport) {
    threadLocal = registry
  }
}

/**
 * @see instantiateThreadLocalRegistry
 */
@fleet.util.multiplatform.Actual()
internal fun instantiateThreadLocalRegistryNative(): com.intellij.platform.syntax.extensions.impl.RegistryHolder =
  ThreadLocalRegistryHolderNative