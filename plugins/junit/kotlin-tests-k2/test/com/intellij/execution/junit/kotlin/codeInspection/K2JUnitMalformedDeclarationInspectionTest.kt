// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.idea.IgnoreJUnit3
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore

@IgnoreJUnit3
@Ignore
class K2JUnitMalformedDeclarationInspectionTestV57 : KotlinJUnitMalformedDeclarationInspectionTestV57() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}

@IgnoreJUnit3
@Ignore
class K2JUnitMalformedDeclarationInspectionTest : KotlinJUnitMalformedDeclarationInspectionTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}

class K2JUnitMalformedDeclarationInspectionTestV6 : KotlinJUnitMalformedDeclarationInspectionTestV6() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}