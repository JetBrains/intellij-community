// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
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
        val checkResultType: Boolean = true,
        val allowParameterless: Boolean = true,
    ) {
        companion object {
            val DEFAULT = Configuration()
        }

        class Builder(
            var checkJvmStaticAnnotation: Boolean,
            var checkParameterType: Boolean,
            var checkResultType: Boolean,
            var allowParameterless: Boolean,
        ) {
            fun build(): Configuration = Configuration(
                checkJvmStaticAnnotation,
                checkParameterType,
                checkResultType,
                allowParameterless
            )
        }

        inline fun with(action: Builder.() -> Unit): Configuration {
            val configuration = this
            return Builder(
                checkJvmStaticAnnotation = configuration.checkJvmStaticAnnotation,
                checkParameterType = configuration.checkParameterType,
                checkResultType = configuration.checkResultType,
                allowParameterless = configuration.allowParameterless,
            ).apply {
                action()
            }.build()
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
        const val MAIN_FUNCTION_NAME: String = "main"

        fun getInstance(): KotlinMainFunctionDetector = service()
    }
}

@RequiresReadLock
fun KotlinMainFunctionDetector.hasMain(file: KtFile, configuration: Configuration = Configuration.DEFAULT): Boolean {
    return findMain(file, configuration) != null
}

@RequiresReadLock
fun KotlinMainFunctionDetector.findMain(file: KtFile, configuration: Configuration = Configuration.DEFAULT): KtNamedFunction? {
    return findMainInContainer(file, configuration)
}

@RequiresReadLock
fun KotlinMainFunctionDetector.hasMain(declaration: KtClassOrObject, configuration: Configuration = Configuration.DEFAULT): Boolean {
    return findMain(declaration, configuration) != null
}

@RequiresReadLock
fun KotlinMainFunctionDetector.findMain(
    declaration: KtClassOrObject,
    configuration: Configuration = Configuration.DEFAULT
): KtNamedFunction? {
    if (declaration is KtObjectDeclaration) {
        if (declaration.isObjectLiteral()) {
            return null
        }

        return findMainInContainer(declaration, configuration)
    }

    return declaration.companionObjects.firstNotNullOfOrNull { findMain(it, configuration) }
}

private fun KotlinMainFunctionDetector.findMainInContainer(owner: KtDeclarationContainer, configuration: Configuration): KtNamedFunction? {
    for (declaration in owner.declarations) {
        ProgressManager.checkCanceled()
        if (declaration is KtNamedFunction && isMain(declaration, configuration)) {
            return declaration
        }
    }

    return null
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

    if (hasMain(containingFile)) {
        return containingFile
    }

    for (descendant in element.descendantsOfType<KtClassOrObject>()) {
        if (hasMain(descendant)) {
            return descendant
        }
    }

    return null
}