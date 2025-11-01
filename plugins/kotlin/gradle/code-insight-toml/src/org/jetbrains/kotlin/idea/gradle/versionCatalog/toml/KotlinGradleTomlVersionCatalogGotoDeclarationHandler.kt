// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT
import org.jetbrains.plugins.gradle.toml.findOriginInTomlFile
import org.jetbrains.plugins.gradle.util.isInVersionCatalogAccessor
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve

/**
 * Enables navigation: `build.gradle.kts` -> TOML Version catalog
 */
class KotlinGradleTomlVersionCatalogGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (!Registry.`is`(GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) return null

        val topmostDotExpression = findTopmostVersionCatalogExpression(sourceElement) ?: return null
        return resolveViaCatalogReferences(topmostDotExpression)
            ?: resolveViaCatalogAccessor(topmostDotExpression)
    }

    private fun resolveViaCatalogReferences(dotExpression: KtDotQualifiedExpression): Array<PsiElement>? =
        dotExpression.references
            .filterIsInstance<KtTomlVersionCatalogReference>()
            .mapNotNull { it.resolve() }
            .toTypedArray()
            .ifEmpty { null }

    private fun resolveViaCatalogAccessor(dotExpression: KtDotQualifiedExpression): Array<PsiElement>? {
        val propertyGetter = dotExpression.toUElement()?.tryResolve()
            ?.safeAs<PsiMethod>()
            ?.takeIf(::isInVersionCatalogAccessor)
            ?: return null
        return findOriginInTomlFile(propertyGetter, dotExpression)
            ?.let { arrayOf(it) }
    }
}
