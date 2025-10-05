// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.extensions

import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.extensions.impl.buildExtensionSupportImpl
import com.intellij.platform.syntax.extensions.impl.performWithExtensionSupportImpl
import com.intellij.platform.syntax.extensions.impl.registry
import org.jetbrains.annotations.ApiStatus
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Provides the current instance of [ExtensionSupport].
 */
@ApiStatus.Experimental
fun currentExtensionSupport(): ExtensionSupport = registry

/**
 * Provides the current instance of [ExtensionSupport] or `null` if it is not supported in the current environment (i.e., in IntelliJ runtime).
 */
@ApiStatus.Experimental
fun currentExtensionRegistry(): ExtensionRegistry? = registry as? ExtensionRegistry

/**
 * Provides access for extensions registered in the current container.
 *
 * Two extension point kinds are supported: plain Extension Points and Language Extension Points.
 *
 * Works both inside and outside of IntelliJ environment.
 * When working inside IJ environment, extensions are picked up from the IJ plugin model.
 * When working outside of IJ environment, extensions must be registered explicitly.
 *
 * @see com.intellij.platform.syntax.SyntaxLanguage
 * @see ExtensionRegistry
 * @see ExtensionPointKey
 * @see performWithExtensionSupport
 * @see buildExtensionSupport
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

/**
 * Marker interface for extension support that does not support dynamic substitution
 */
@ApiStatus.Experimental
interface StaticExtensionSupport

/**
 * Runs [action] with [support] installed as the current instance of [ExtensionSupport].
 * The previous instance is restored on method exit.
 */
@OptIn(ExperimentalContracts::class)
fun <T> performWithExtensionSupport(support: ExtensionSupport, action: (ExtensionSupport) -> T): T {
  contract {
    callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
  }
  return performWithExtensionSupportImpl(support, action)
}

/**
 * Builds [ExtensionSupport] instance.
 * It is not installed as the current instance of [ExtensionSupport].
 */
fun buildExtensionSupport(block: ExtensionRegistry.() -> Unit): ExtensionSupport =
  buildExtensionSupportImpl(block)
