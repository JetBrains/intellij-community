// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2JUnitParameterizedSourceGoToRelatedTest : KotlinJUnitParameterizedSourceGoToRelatedTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}