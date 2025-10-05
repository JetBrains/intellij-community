// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2JUnitMixedFrameworkInspectionTest : KotlinJUnitMixedFrameworkInspectionTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun `test junit 5 test case with junit 4 quickfix`() {
    super.`test junit 5 test case with junit 4 quickfix`()
  }
}