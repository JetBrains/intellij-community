// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import com.intellij.platform.syntax.extensions.ExtensionSupport
import fleet.util.multiplatform.linkToActual

internal val registry: ExtensionSupport by lazy {
  instantiateExtensionRegistry()
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
