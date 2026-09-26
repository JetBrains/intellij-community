// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT

/**
 * Enables navigation: `build.gradle.kts` -> Gradle Version Catalog
 * TODO: IDEA-387965 move the class to another module, it's not bound to TOML anymore
 */
class KotlinGradleVersionCatalogGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (!Registry.`is`(GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT, false)) return null

        val topmostDotExpression = findTopmostVersionCatalogExpression(sourceElement) ?: return null
        return resolveViaCatalogReferences(topmostDotExpression)
    }

    private fun resolveViaCatalogReferences(dotExpression: KtDotQualifiedExpression): Array<PsiElement>? =
        dotExpression.references
            .filterIsInstance<KtVersionCatalogReference>()
            .mapNotNull { it.resolve() }
            .toTypedArray()
            .ifEmpty { null }
}
