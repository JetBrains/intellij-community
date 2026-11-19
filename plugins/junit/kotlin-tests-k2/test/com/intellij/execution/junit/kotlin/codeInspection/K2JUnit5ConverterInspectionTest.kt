// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class K2JUnit5ConverterInspectionTest : KotlinJUnit5ConverterInspectionTest() {

  @Test
  override fun `test remove public modifier`() {
    super.`test remove public modifier`()
  }
}