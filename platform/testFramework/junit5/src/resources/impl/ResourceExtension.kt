// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.impl

import com.intellij.testFramework.junit5.resources.ResourceExtensionApi
import com.intellij.testFramework.junit5.resources.providers.ResourceProvider
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*

/**
 * To be used by [Extension] that delegate it to [ResourceExtensionImpl]
 * ```kotlin
 * class FooExtension: ResourceExtension by ResourceExtensionImpl
 * ```
 */
@TestOnly
interface ResourceExtension<R : Any, PR : ResourceProvider<R>> :
  BeforeEachCallback,
  AfterEachCallback,
  BeforeAllCallback,
  AfterAllCallback,
  ResourceExtensionApi<R, PR>,
  ParameterResolver