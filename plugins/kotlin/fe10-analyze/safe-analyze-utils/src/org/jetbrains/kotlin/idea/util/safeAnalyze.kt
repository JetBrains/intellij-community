/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.util

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.psi.PsiElement
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.ALL_KOTLIN_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.lazy.NoDescriptorForDeclarationException
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Best effort to analyze element:
 * - Best effort for file that is out of source root scope: NoDescriptorForDeclarationException could be swallowed
 * - Do not swallow NoDescriptorForDeclarationException during analysis for in source scope files
 */
inline fun <T> PsiElement.actionUnderSafeAnalyzeBlock(
    crossinline action: () -> T,
    crossinline fallback: () -> T
): T = try {
    action()
} catch (e: Exception) {
    e.returnIfNoDescriptorForDeclarationException(condition = {
        val file = containingFile
        it && (!file.isPhysical || !file.isUnderKotlinSourceRootTypes())
    }) { fallback() }
}

val Exception.isItNoDescriptorForDeclarationException: Boolean
    get() = this is NoDescriptorForDeclarationException || cause?.safeAs<Exception>()?.isItNoDescriptorForDeclarationException == true

inline fun <T> Exception.returnIfNoDescriptorForDeclarationException(
    crossinline condition: (Boolean) -> Boolean = { v -> v },
    crossinline computable: () -> T
): T =
    if (condition(this.isItNoDescriptorForDeclarationException)) {
        computable()
    } else {
        throw this
    }

val KOTLIN_AWARE_SOURCE_ROOT_TYPES: Set<JpsModuleSourceRootType<JavaSourceRootProperties>> =
    JavaModuleSourceRootTypes.SOURCES + ALL_KOTLIN_SOURCE_ROOT_TYPES

fun PsiElement?.isUnderKotlinSourceRootTypes(): Boolean {
    val ktFile = this?.containingFile.safeAs<KtFile>() ?: return false
    val file = ktFile.virtualFile?.takeIf { it !is VirtualFileWindow && it.fileSystem !is NonPhysicalFileSystem } ?: return false
    val projectFileIndex = ProjectRootManager.getInstance(ktFile.project).fileIndex
    return projectFileIndex.isUnderSourceRootOfType(file, KOTLIN_AWARE_SOURCE_ROOT_TYPES)
}
