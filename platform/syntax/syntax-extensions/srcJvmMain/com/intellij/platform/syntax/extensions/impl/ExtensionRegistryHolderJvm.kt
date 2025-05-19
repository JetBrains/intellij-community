// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.extensions.ExtensionSupport
import fleet.util.multiplatform.Actual

/**
 * Tries to find [com.intellij.platform.syntax.psi.extensions.IntelliJExtensionSupport] in the class path.
 * If it succeeds, it means we are running in IntelliJ, and we must use IJ plugin model as the [ExtensionSupport] backend.
 * Otherwise, returning [ExtensionRegistryImpl].
 *
 * @see instantiateExtensionRegistry
 */
@Actual("instantiateExtensionRegistry")
internal fun instantiateExtensionRegistryJvm(): ExtensionSupport {
  try {
    val clazz = Class.forName("com.intellij.platform.syntax.psi.extensions.IntelliJExtensionSupport")
    val instance = clazz.getConstructor().newInstance()
    return instance as ExtensionSupport
  }
  catch (_: Throwable) {
    return ExtensionRegistryImpl
  }
}
