// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SafeAnalysisUtils")
package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiElement
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.config.ALL_KOTLIN_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.base.util.isUnderKotlinSourceRootTypes
import org.jetbrains.kotlin.idea.statistics.KotlinFailureCollector
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.lazy.NoDescriptorForDeclarationException
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Best effort to analyze element:
 * - Best effort for file that is out of source root scope: NoDescriptorForDeclarationException could be swallowed
 * - Do not swallow NoDescriptorForDeclarationException during analysis for in source scope files
 */
@K1Deprecation
inline fun <T> PsiElement.actionUnderSafeAnalyzeBlock(
    crossinline action: () -> T,
    crossinline fallback: () -> T
): T = try {
    action()
} catch (e: Exception) {
    e.returnIfNoDescriptorForDeclarationException(condition = {
        val file = containingFile
        val condition = it && (!file.isPhysical || !file.isUnderKotlinSourceRootTypes())
        if (!condition) (file as? KtFile)?.let { KotlinFailureCollector.recordGeneralFrontEndFailureEvent(it) }
        condition
    }) { fallback() }
}

@K1Deprecation
val Exception.isItNoDescriptorForDeclarationException: Boolean
    get() = this is NoDescriptorForDeclarationException || cause?.safeAs<Exception>()?.isItNoDescriptorForDeclarationException == true

@K1Deprecation
inline fun <T> Exception.returnIfNoDescriptorForDeclarationException(
    crossinline condition: (Boolean) -> Boolean = { v -> v },
    crossinline computable: () -> T
): T =
    if (condition(this.isItNoDescriptorForDeclarationException)) {
        computable()
    } else {
        throw this
    }

@K1Deprecation
val KOTLIN_AWARE_SOURCE_ROOT_TYPES: Set<JpsModuleSourceRootType<JavaSourceRootProperties>> =
    JavaModuleSourceRootTypes.SOURCES + ALL_KOTLIN_SOURCE_ROOT_TYPES

