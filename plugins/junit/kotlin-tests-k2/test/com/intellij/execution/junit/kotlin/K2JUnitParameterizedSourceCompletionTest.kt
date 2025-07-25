// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2JUnitParameterizedSourceCompletionTest : KotlinJUnitParameterizedSourceCompletionTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}