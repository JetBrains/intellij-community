// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal fun KtFile.fileCanBeAffectedByCompilerPlugins(project: Project): Boolean {
    if (isCompiled) {
        // files from libraries cannot have compiler plugins, this is a fast path as calling `getKaModule` is more expensive
        return false
    }
    val module = getKaModule(project, useSiteModule = null)
    if (module !is KaSourceModule) {
        // only source modules can have compiler plugins
        return false
    }
    val pluginsProvider = KotlinCompilerPluginsProvider.getInstance(project) ?: return false
    val extensions = pluginsProvider.getRegisteredExtensions(module, FirExtensionRegistrarAdapter)
    return extensions.isNotEmpty()
}

/**
 * Only classes and members of such classes that are annotated with special compiler plugin annotations or that inherit from such a class can be modified by compiler plugins
 */
internal fun KtDeclaration.declarationCanBeModifiedByCompilerPlugins(): Boolean {
    return when (this) {
        is KtClassOrObject -> {
            annotationEntries.isNotEmpty()
                    || superTypeListEntries.isNotEmpty()
                    || this is KtObjectDeclaration && isCompanion() && containingClassOrObject?.declarationCanBeModifiedByCompilerPlugins() == true
        }

        is KtCallableDeclaration -> {
            val containingClass = containingClassOrObject
            containingClass != null && containingClass.declarationCanBeModifiedByCompilerPlugins()
        }

        else -> false
    }
}
