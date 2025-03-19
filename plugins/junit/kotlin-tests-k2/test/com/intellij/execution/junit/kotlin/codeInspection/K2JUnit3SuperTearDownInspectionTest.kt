// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2JUnit3SuperTearDownInspectionTest : KotlinJUnit3SuperTearDownInspectionTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}