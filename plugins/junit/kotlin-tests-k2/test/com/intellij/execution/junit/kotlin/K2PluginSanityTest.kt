// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

class K2PluginSanityTest : BasePlatformTestCase() {
  fun testK2PluginEnabled() {
    assertTrue(KotlinPluginModeProvider.isK2Mode())
  }

  fun testK1PluginDisabled() {
    assertFalse(KotlinPluginModeProvider.isK1Mode())
  }
}