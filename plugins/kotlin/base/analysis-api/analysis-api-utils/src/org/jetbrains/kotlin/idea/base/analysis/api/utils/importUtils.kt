// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.imports.KaDefaultImports
import org.jetbrains.kotlin.analysis.api.imports.getDefaultImports
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

@ApiStatus.Internal
fun KtFile.getDefaultImports(useSiteModule: KaModule?): KaDefaultImports {
    val project = project
    val kaModule = KaModuleProvider.getModule(project, this, useSiteModule)
    return kaModule.targetPlatform.getDefaultImports(project)
}

@ApiStatus.Internal
fun KtFile.getDefaultImportPaths(useSiteModule: KaModule?): List<ImportPath> {
    return getDefaultImports(useSiteModule).defaultImports.map { it.importPath }
}