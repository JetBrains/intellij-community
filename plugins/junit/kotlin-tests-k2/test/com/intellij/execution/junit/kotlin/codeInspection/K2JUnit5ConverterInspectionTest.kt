// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.idea.IJIgnore
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2JUnit5ConverterInspectionTest : KotlinJUnit5ConverterInspectionTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  @IJIgnore(issue = "KTIJ-35166")
  override fun `test remove public modifier`() {
    super.`test remove public modifier`()
  }
}