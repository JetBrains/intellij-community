// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.RegistryKeyExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Sets the [value] for the Registry [key], runs, and reverts to previous value.
 * @see com.intellij.openapi.util.registry.RegistryValue.setValue
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(RegistryKeyExtension::class)
annotation class RegistryKey(val key: String, val value: String)