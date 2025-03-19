// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1JUnit5AcceptanceTest : KotlinJUnit5AcceptanceTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1
}