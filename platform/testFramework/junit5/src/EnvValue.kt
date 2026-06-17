// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.EnvValueExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Sets a [environment variable][com.intellij.util.EnvironmentUtil] before a method or a class,
 * and resets to the previous value after the execution is finished.
 *
 * [org.junit.jupiter.api.TestFactory] and [org.junit.jupiter.api.TestTemplate] methods are not supported.
 *
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(EnvValueExtension::class)
annotation class EnvValue(
  val envName: String,
  val value: String,
)

/**
 * Same as [EnvValue], but sets the value in [org.junit.jupiter.api.extension.BeforeAllCallback]
 * so it also covers JUnit extensions and fixtures initialized before test methods.
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.CLASS)
@ExtendWith(EnvValueExtension::class)
annotation class EnvValueClassLevel(
  val envName: String,
  val value: String,
)
