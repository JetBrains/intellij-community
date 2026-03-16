// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.testFramework.junit5.impl.EnableTracingExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

/**
 * Enables tracing of the specified logging categories inside the annotated test class or method.
 * The corresponding messages will be present in the log file on disk.
 * Also, they will be visible in <DEBUG log> which is printed to str-error if the test fails.
 *
 * After the annotated test/function finishes, the logger will be restored to DEBUG level.
 *
 * For convenience, you can specify logging categories by their fully qualified names and/or by their classes.
 * If both categories and categoryClasses are specified, all of them will be enabled.
 */
@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(EnableTracingExtension::class)
annotation class EnableTracingFor(
  val categories: Array<String> = [],
  val categoryClasses: Array<KClass<*>> = [],
)