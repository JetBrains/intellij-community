// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.providers

import org.jetbrains.annotations.TestOnly
import kotlin.reflect.KClass


/**
 * When [ResourceProvider] "ProviderA" creates "A", it is stored in context.
 * Another provider might want to get it.
 * Think about module provider looking for the project injected by another provider
 */
@TestOnly
interface ResourceStorage {
  fun <R : Any, PR : ResourceProvider<R>> getResourceCreatedByProvider(provider: KClass<out PR>,
                                                                       resourceType: KClass<R>): Result<R>
}