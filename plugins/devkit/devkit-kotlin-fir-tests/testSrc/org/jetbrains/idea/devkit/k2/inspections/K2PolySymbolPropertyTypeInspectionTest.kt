// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import org.jetbrains.idea.devkit.kotlin.inspections.KtPolySymbolPropertyTypeInspectionTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

internal class K2PolySymbolPropertyTypeInspectionTest : KtPolySymbolPropertyTypeInspectionTest() {
  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2
}