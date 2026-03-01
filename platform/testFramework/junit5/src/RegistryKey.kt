// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.impl.RegistryKeyExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Sets the [value] for the [Registry] [key] before an annotated test method (or a class),
 * and reverts to the previous value after an annotated test method (or, respectively, a class).
 *
 * This annotation is preferred over manual [Registry.get] + [com.intellij.openapi.util.registry.RegistryValue.setValue] calls
 * as it automatically handles cleanup after test completion.
 *
 * @see com.intellij.openapi.util.registry.RegistryValue.setValue
 * @see TestDisposable alternative for manual registry value management
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(RegistryKeyExtension::class)
annotation class RegistryKey(val key: String, val value: String)

/**
 * Same as [Registry] but sets key in [org.junit.jupiter.api.extension.BeforeAllCallback],
 * so can be used to configure services that start before the test (i.e. projectActivity)
 */
@TestOnly
@Repeatable
@TestApplication
@Target(AnnotationTarget.CLASS)
@ExtendWith(RegistryKeyExtension::class)
annotation class RegistryKeyAppLevel(val key: String, val value: String)
