// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.extensions

import com.intellij.platform.syntax.extensions.impl.registry
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the current instance of [ExtensionRegistry].
 */
@ApiStatus.Experimental
fun ExtensionSupport(): ExtensionSupport = registry

/**
 * Provides the current instance of [ExtensionRegistry] or `null` if it is not supported in the current environment (i.e., in IntelliJ runtime).
 */
@ApiStatus.Experimental
fun ExtensionRegistry(): ExtensionRegistry? = registry as? ExtensionRegistry

/**
 * Provides access for extensions registered in the current container.
 *
 * Two extension point kinds are supported: plain Extension Points and Language Extension Points.
 *
 * Works both inside and outside of IntelliJ environment.
 * When working inside IJ environment, extensions are picked up from the IJ plugin model.
 * When working outside of IJ environment, extensions must be registered explicitly.
 *
 * @See ExtensionKey
 * @see SyntaxLanguage
 * @see ExtensionRegistry
 */
@ApiStatus.Experimental
interface ExtensionSupport {
  fun <T : Any> getExtensions(extensionPoint: ExtensionPointKey<T>): List<T>
  fun <T : Any> getLanguageExtensions(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage): List<T>
}

/**
 * Allows registering extensions for [ExtensionSupport].
 * It is not supported in IntelliJ runtime. IJ plugin model is used instead.
 */
@ApiStatus.Experimental
interface ExtensionRegistry : ExtensionSupport {
  fun <T : Any> registerExtension(extensionPoint: ExtensionPointKey<T>, extension: T)
  fun <T : Any> unregisterExtension(extensionPoint: ExtensionPointKey<T>, extension: T)

  fun <T : Any> registerLanguageExtension(extensionPoint: ExtensionPointKey<T>, extension: T, language: SyntaxLanguage)
  fun <T : Any> unregisterLanguageExtension(extensionPoint: ExtensionPointKey<T>, language: SyntaxLanguage)
}
