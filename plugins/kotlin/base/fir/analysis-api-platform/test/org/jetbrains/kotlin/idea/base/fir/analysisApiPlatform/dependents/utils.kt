// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.dependents

import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind

internal fun renderModuleName(module: KaModule): String {
    val sourceModule = module as KaSourceModule
    return "${sourceModule.name} (${sourceModule.sourceModuleKind.toString().lowercase()})"
}
