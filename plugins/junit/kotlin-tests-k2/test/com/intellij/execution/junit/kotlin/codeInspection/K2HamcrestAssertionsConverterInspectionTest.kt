// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.idea.IgnoreJUnit3
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore

@IgnoreJUnit3
@Ignore
class K2HamcrestAssertionsConverterInspectionTest : KotlinHamcrestAssertionsConverterInspectionTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}