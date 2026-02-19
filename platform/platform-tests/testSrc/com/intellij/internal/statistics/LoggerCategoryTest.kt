// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.logging.Logger

class LoggerCategoryTest : BasePlatformTestCase() {

  fun testFileLoggerCategory() {
    val julLogger = logger as JulLogger
    // Not the best way to get logger category, but I don't want to expose any properties for this test
    val myLoggerField = JulLogger::class.java.getDeclaredField("myLogger") ?: kotlin.test.fail("No field MyLogger")
    myLoggerField.trySetAccessible()
    val javaLogger = myLoggerField.get(julLogger) as? Logger?: kotlin.test.fail("myLogger is null")
    kotlin.test.assertEquals("#${LoggerCategoryTest::class.qualifiedName}Kt", javaLogger.name)
  }
}

private val logger = fileLogger()