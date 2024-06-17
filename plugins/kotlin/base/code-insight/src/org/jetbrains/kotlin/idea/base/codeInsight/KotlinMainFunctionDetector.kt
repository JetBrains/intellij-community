// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
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
        val checkStrictlyOneMain: Boolean = false
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

        @ApiStatus.Internal
        fun getInstanceDumbAware(project: Project): KotlinMainFunctionDetector {
            return if (DumbService.isDumb(project)) PsiOnlyKotlinMainFunctionDetector
            else getInstance()
        }
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
    val mains = owner
        .declarations
        .asSequence()
        .filterIsInstance<KtNamedFunction>()
        .filter { isMain(it, configuration) }

    return if (configuration.checkStrictlyOneMain) mains.singleOrNull()
    else mains.firstOrNull()
}

@RequiresReadLock
fun KotlinMainFunctionDetector.findMainOwner(element: PsiElement): KtDeclarationContainer? {
    return findMainOwnerDumbAware(element)
}

private fun KotlinMainFunctionDetector.findMainOwnerDumbAware(element: PsiElement): KtDeclarationContainer? {
    val project = element.project
    val dumbService = DumbService.getInstance(project)

    if (!dumbService.isDumb || this !is PsiOnlyKotlinMainFunctionDetector) return findMainFunctionContainer(element)

    return CachedValuesManager.getManager(project).getCachedValue(element) {
        val mainOwner = findMainFunctionContainer(element, Configuration(checkStrictlyOneMain = true))

        Result.create(mainOwner, PsiModificationTracker.getInstance(project), dumbService.modificationTracker)
    }
}

private fun KotlinMainFunctionDetector.findMainFunctionContainer(
    element: PsiElement,
    configuration: Configuration = Configuration.DEFAULT
): KtDeclarationContainer? {
    val containingFile = element.containingFile as? KtFile ?: return null
    if (!RootKindFilter.projectSources.matches(containingFile)) {
        return null
    }

    for (parent in element.parentsWithSelf) {
        if (parent is KtClassOrObject && hasMain(parent, configuration)) {
            return parent
        }
    }

    if (hasMain(containingFile, configuration)) {
        return containingFile
    }

    for (descendant in element.descendantsOfType<KtClassOrObject>()) {
        if (hasMain(descendant, configuration)) {
            return descendant
        }
    }

    return null
}