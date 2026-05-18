// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.SystemPropertyExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Sets a [system property value][System.setProperty] before a method or a class,
 * and resets to the previous value after the execution is finished.
 *
 * [org.junit.jupiter.api.TestFactory] and [org.junit.jupiter.api.TestTemplate] methods are not supported.
 *
 * @param propertyKey property key to set
 * @param propertyValue property value to set, or an empty string to [clear the value][System.clearProperty]
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(SystemPropertyExtension::class)
annotation class SystemProperty(
  val propertyKey: String,
  val propertyValue: String,
)

/**
 * Same as [SystemProperty], but sets the property in [org.junit.jupiter.api.extension.BeforeAllCallback]
 * so it also covers JUnit extensions and fixtures initialized before test methods.
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.CLASS)
@ExtendWith(SystemPropertyExtension::class)
annotation class SystemPropertyClassLevel(
  val propertyKey: String,
  val propertyValue: String,
)
