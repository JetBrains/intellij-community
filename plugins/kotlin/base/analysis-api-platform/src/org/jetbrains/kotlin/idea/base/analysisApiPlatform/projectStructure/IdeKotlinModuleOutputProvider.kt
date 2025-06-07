// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform.projectStructure

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleOutputProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule

internal class IdeKotlinModuleOutputProvider : KotlinModuleOutputProvider {
    override fun getCompilationOutput(module: KaSourceModule): VirtualFile? {
        return CompilerModuleExtension.getInstance(module.openapiModule)?.compilerOutputPath
    }
}