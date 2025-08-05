// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection.deadCode

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection

class K2JUnit5ImplicitUsageProviderTest : KotlinJUnit5ImplicitUsageProviderTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override val inspection: InspectionProfileEntry by lazy { UnusedSymbolInspection() }
}