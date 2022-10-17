// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector.Configuration
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

@ApiStatus.Internal
interface KotlinMainFunctionDetector {
    class Configuration(
        val checkJvmStaticAnnotation: Boolean = true,
        val checkParameterType: Boolean = true,
        val checkResultType: Boolean = true
    ) {
        companion object {
            val DEFAULT = Configuration()
        }
    }

    /**
     * Checks if a given function satisfies 'main()' function contracts.
     *
     * Service implementations perform resolution.
     * See 'PsiOnlyKotlinMainFunctionDetector' for PSI-only heuristic-based checker.
     */
    @RequiresReadLock
    fun isMain(function: KtNamedFunction, configuration: Configuration = Configuration.DEFAULT): Boolean

    companion object {
        fun getInstance(): KotlinMainFunctionDetector = service()
    }
}

@RequiresReadLock
fun KotlinMainFunctionDetector.hasMain(file: KtFile, configuration: Configuration = Configuration.DEFAULT): Boolean {
    return file.declarations.any { it is KtNamedFunction && isMain(it, configuration) }
}

@RequiresReadLock
fun KotlinMainFunctionDetector.hasMain(declaration: KtClassOrObject, configuration: Configuration = Configuration.DEFAULT): Boolean {
    if (declaration is KtObjectDeclaration) {
        return !declaration.isObjectLiteral()
                && declaration.declarations.any { it is KtNamedFunction && isMain(it, configuration) }
    }

    return declaration.companionObjects.any { hasMain(it, configuration) }
}

@RequiresReadLock
fun KotlinMainFunctionDetector.findMainOwner(element: PsiElement): KtDeclarationContainer? {
    val containingFile = element.containingFile as? KtFile ?: return null
    if (!RootKindFilter.projectSources.matches(containingFile)) {
        return null
    }

    for (parent in element.parentsWithSelf) {
        if (parent is KtClassOrObject && hasMain(parent)) {
            return parent
        }
    }

    for (descendant in element.descendantsOfType<KtClassOrObject>()) {
        if (hasMain(descendant)) {
            return descendant
        }
    }

    if (hasMain(containingFile)) {
        return containingFile
    }

     return null
}