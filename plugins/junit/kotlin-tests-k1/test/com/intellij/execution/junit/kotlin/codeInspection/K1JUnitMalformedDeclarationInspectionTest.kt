// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1JUnitMalformedDeclarationInspectionTestV57 : KotlinJUnitMalformedDeclarationInspectionTestV57() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1
}

class K1JUnitMalformedDeclarationInspectionTestLatest : KotlinJUnitMalformedDeclarationInspectionTestLatest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1
  override val pluginVersion: String = "K1"
}