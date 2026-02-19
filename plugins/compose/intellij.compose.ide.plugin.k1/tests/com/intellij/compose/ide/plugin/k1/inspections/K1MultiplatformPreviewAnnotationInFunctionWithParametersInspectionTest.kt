// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k1.inspections

import com.intellij.compose.ide.plugin.shared.inspections.MultiplatformPreviewAnnotationInFunctionWithParametersInspectionTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

internal class K1MultiplatformPreviewAnnotationInFunctionWithParametersInspectionTest(previewPackageName: String, parametersPackageName: String)
  : MultiplatformPreviewAnnotationInFunctionWithParametersInspectionTest(previewPackageName, parametersPackageName) {
  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K1
}
