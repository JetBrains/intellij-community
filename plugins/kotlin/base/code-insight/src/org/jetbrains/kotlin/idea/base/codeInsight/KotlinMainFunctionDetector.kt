// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
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
import org.jetbrains.kotlin.idea.base.util.isInDumbMode
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
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
    return if (owner is KtElement && owner.project.isInDumbMode) findMainInContainerDumb(owner, configuration)
    else findMainInContainerSmart(owner, configuration)
}

private fun KotlinMainFunctionDetector.findMainInContainerDumb(
    owner: KtDeclarationContainer,
    configuration: Configuration
): KtNamedFunction? {
    if (owner !is KtElement) return null

    return CachedValuesManager.getManager(owner.project).getCachedValue(owner) {
        val mainOwner = findAllMainsInContainer(owner, configuration).singleOrNull()

        val project = owner.project
        Result.create(mainOwner, PsiModificationTracker.getInstance(project), DumbService.getInstance(project).modificationTracker)
    }
}

private fun KotlinMainFunctionDetector.findMainInContainerSmart(
    owner: KtDeclarationContainer,
    configuration: Configuration
): KtNamedFunction? {
    return findAllMainsInContainer(owner, configuration).firstOrNull()
}

private fun KotlinMainFunctionDetector.findAllMainsInContainer(
    owner: KtDeclarationContainer,
    configuration: Configuration
): Sequence<KtNamedFunction> {
     return owner
        .declarations
        .asSequence()
        .filterIsInstance<KtNamedFunction>()
        .filter {
            ProgressManager.checkCanceled()
            isMain(it, configuration)
        }
}

/**
 * NOTE: In dumbMode it will return null if a container contains more than one main [findMainInContainerDumb]
 *
 * @return Main function container.
 */
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