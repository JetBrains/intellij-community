// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Some classes are actually implementation details, and we don't need to show them to the user.
 * For example,
 * - base class of *.gradle.kts buildscript
 * - the snippet in the Kotlin Notebook cell
 *
 * This extension point allows providing a checker for such classes.
 * The context is expected to be determined by the provided `contextElement`.
 */
@ApiStatus.Internal
interface ImplementationDetailClassNameCheckerProvider {
    /**
     * Returns a checker applicable for a provided context element, or null if no checker is applicable.
     */
    fun get(contextElement: PsiElement): ImplementationDetailClassNameChecker?

    companion object {
        internal val EP_NAME: ExtensionPointName<ImplementationDetailClassNameCheckerProvider> =
            ExtensionPointName.Companion.create("org.jetbrains.kotlin.implementationDetailClassNameCheckerProvider")

        fun get(contextElement: PsiElement?): ImplementationDetailClassNameChecker {
            if (contextElement == null) return ImplementationDetailClassNameChecker.Default
            return EP_NAME.extensionList.firstNotNullOfOrNull {
                it.get(contextElement)
            } ?: ImplementationDetailClassNameChecker.Default
        }
    }
}

/**
 * Checker interface for determining if a class name represents an implementation detail.
 * Implementation details are classes that should not be exposed to users, such as base classes
 * of Gradle buildscripts or notebook cell snippets.
 *
 * The interface provides a single method to check if a given class name represents an implementation detail.
 * A default implementation [Default] considers all names as normal ones.
 */
@ApiStatus.Internal
fun interface ImplementationDetailClassNameChecker {
    fun isImplementationDetail(className: String): Boolean

    object Default : ImplementationDetailClassNameChecker {
        override fun isImplementationDetail(className: String): Boolean = false
    }
}
