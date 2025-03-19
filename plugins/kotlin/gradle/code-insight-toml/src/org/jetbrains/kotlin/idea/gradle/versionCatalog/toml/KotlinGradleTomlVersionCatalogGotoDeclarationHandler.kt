// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension
import org.jetbrains.plugins.gradle.toml.findOriginInTomlFile
import org.jetbrains.plugins.gradle.util.isInVersionCatalogAccessor
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve

/**
 * Enables navigation to a library in a Version catalog from its reference in `build.gradle.kts`.
 * Relies on `SyntheticVersionCatalogAccessor`, which generates classes providing getters for properties of Gradle Version catalogs.
 * UAST element for a dot-qualified expression could be resolved to such a getter.
 *
 * @see [org.jetbrains.plugins.gradle.service.resolve.static.SyntheticVersionCatalogAccessor]
 */
class KotlinGradleTomlVersionCatalogGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (sourceElement !is LeafPsiElement) return null
        if (!Registry.`is`(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) {
            return null
        }
        val grandParent = sourceElement.parent?.parent ?: return null
        if (grandParent !is KtDotQualifiedExpression) return null
        val fullExpression = findFullVersionCatalogExpression(grandParent)
        val propertyGetter = fullExpression.toUElement()?.tryResolve()
            ?.safeAs<PsiMethod>()
            ?.takeIf(::isInVersionCatalogAccessor)
            ?: return null
        return findOriginInTomlFile(propertyGetter, fullExpression)
            ?.let { arrayOf(it) }
    }

    /**
     * Finds the full dot-qualified expression referring to a version catalog variable.
     * E.g., if the element is `libs.aaa` in `libs.aaa.b.c` (or in `libs.aaa.b.c.get()`), it will return the element corresponding to
     * `libs.aaa.b.c` (but before `.get()`).
     */
    private fun findFullVersionCatalogExpression(element: KtDotQualifiedExpression): PsiElement {
        var fullExpression = element
        while (fullExpression.hasWrappingVersionCatalogExpression()) {
            fullExpression = fullExpression.parent as KtDotQualifiedExpression
        }
        return fullExpression
    }
}
