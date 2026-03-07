/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.base.analysisApiPlatform.projectStructure

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaModuleConverter
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule

@OptIn(KaIdeApi::class)
internal class KaIdeModuleConverter: KaModuleConverter {
    override fun asOpenApiModule(module: KaModule): Module? {
        return (module as? KaSourceModule)?.openapiModule
    }
}
