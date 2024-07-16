// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources

import com.intellij.testFramework.junit5.resources.ResourceExtensionApi.Companion.forProvider
import com.intellij.testFramework.junit5.resources.impl.ResourceExtensionImpl
import com.intellij.testFramework.junit5.resources.providers.ParameterizableResourceProvider
import com.intellij.testFramework.junit5.resources.providers.ResourceProvider
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.Extension

/**
 * User API.
 * 1. Call [forProvider] with [PR] to create a resource extension and [R] will be injected in fields and params.
 * 2. Call [create] extensions methods below to create more [R] if needed
 *
 * [R] resource (like module or project)
 * [PR] [ResourceProvider]
 */
@TestOnly
@NonExtendable
interface ResourceExtensionApi<R : Any, PR : ResourceProvider<R>> : Extension {
  companion object {
    /**
     * To create instances with method lifespan
     * ```kotlin
     * class Foo {
     *     val extField = forProvider(ModuleProvider())
     * }
     * ```
     * For class lifespan, use static field.
     * [ResourceProvider] has also [asExtension]
     */
    @TestOnly
    fun <R : Any, RP : ResourceProvider<R>> forProvider(provider: RP): ResourceExtensionApi<R, RP> = ResourceExtensionImpl(provider)
  }
}

/**
 * You can create as many [R] as you need.
 * All [R] guaranteed to be different, and will be deleted at the end of the scope (class or method)
 * unless [disposeOnExit] not set.
 */
@TestOnly
suspend fun <R : Any, PR : ResourceProvider<R>> ResourceExtensionApi<R, PR>.create(disposeOnExit: Boolean = true): R =
  ResourceExtensionImpl.createManually(this, disposeOnExit)

/**
 * As [create] but with [params] (for [ResourceProvider]s that support it)
 */
@TestOnly
suspend fun <R : Any, P, PR : ParameterizableResourceProvider<R, P>> ResourceExtensionApi<R, PR>.create(params: P,
                                                                                                        disposeOnExit: Boolean = true) =
  ResourceExtensionImpl.createManually(this, params, disposeOnExit)