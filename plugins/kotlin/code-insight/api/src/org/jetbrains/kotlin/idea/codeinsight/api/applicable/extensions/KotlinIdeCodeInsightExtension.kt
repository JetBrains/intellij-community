// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode


/**
 * Extension point for amending the behavior of the Kotlin IDE code insight.
 * General contract: method should return null if it's not applicable for the passed [PsiElement].
 */
@ApiStatus.Internal
interface KotlinIdeCodeInsightExtension {
    /**
     * Choose the resolution mode for a given [contextElement].
     * The method is called only when the [contextElement] is inside a dangling file.
     */
    fun chooseDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode?

    /**
     * Allows amending the type rendered for a given [contextElement].
     * This method is called some code-generating refactorings, such as "Introduce variable" or "Extract method".
     */
    fun modifyRenderedType(contextElement: PsiElement, renderedType: String): String?

    /**
     * Allows deciding whether declarations inside this [container] should be added before or after the usage.
     * This method is called for some cases of "Create from usage" refactorings.
     */
    fun shouldAddDeclarationBeforeUsage(container: PsiElement): Boolean?

    companion object {
        internal val EP_NAME: ExtensionPointName<KotlinIdeCodeInsightExtension> =
            ExtensionPointName.Companion.create("org.jetbrains.kotlin.ideCodeInsightExtension")
    }
}

/**
 * Employs [KotlinIdeCodeInsightExtension]s to choose the resolution mode for a given [contextElement].
 * Returns [KaDanglingFileResolutionMode.PREFER_SELF] if no extensions are applicable.
 */
fun chooseDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode {
    return KotlinIdeCodeInsightExtension.EP_NAME.extensionList.mapNotNull { extension ->
        extension.chooseDanglingFileResolutionMode(contextElement)
    }.singleOrNull() ?: KaDanglingFileResolutionMode.PREFER_SELF
}

/**
 * Employs [KotlinIdeCodeInsightExtension]s to decide
 * whether declarations inside this [container] should be added before or after the usage.
 * Returns true iff none of the extensions returned false and at least one returned true.
 */
fun shouldAddDeclarationBeforeUsage(container: PsiElement): Boolean {
    val default = false
    return KotlinIdeCodeInsightExtension.EP_NAME.extensionList.fold(default) { result, extension ->
        when (extension.shouldAddDeclarationBeforeUsage(container)) {
            default -> return default
            !default -> !default
            else -> result
        }
    }
}

/**
 * Employs [KotlinIdeCodeInsightExtension]s to modify the [renderedType] for a given [contextElement].
 * Returns unchanged [renderedType] if no extensions are applicable.
 */
fun modifyRenderedType(contextElement: PsiElement, renderedType: String): String {
    return KotlinIdeCodeInsightExtension.EP_NAME.extensionList.fold(renderedType) { type, extension ->
        extension.modifyRenderedType(contextElement, type) ?: type
    }
}
