// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.testFramework.junit5.impl.LogLevelExtension
import com.intellij.testFramework.junit5.impl.LogLevelWithClassExtension
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KClass

@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(LogLevelExtension::class)
annotation class LogLevel(
  val category: String,
  val level: LogLevel,
)

@TestOnly
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@ExtendWith(LogLevelWithClassExtension::class)
annotation class LogLevelWithClass(
  val category: KClass<*>,
  val level: LogLevel,
)