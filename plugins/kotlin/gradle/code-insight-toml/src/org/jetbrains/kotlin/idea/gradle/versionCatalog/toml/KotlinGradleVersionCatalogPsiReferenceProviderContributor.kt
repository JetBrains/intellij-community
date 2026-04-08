// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.references.KotlinPsiReferenceProviderContributor
import org.jetbrains.kotlin.references.KotlinPsiReferenceProviderContributor.ReferenceProvider
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles

/**
 * Creates references from `build.gradle.kts` to Gradle Version Catalog.
 * TODO: IDEA-387965 move the class to another module, it's not bound to TOML anymore
 */
internal class KotlinGradleVersionCatalogPsiReferenceProviderContributor : KotlinPsiReferenceProviderContributor<KtDotQualifiedExpression> {
    override val elementClass: Class<KtDotQualifiedExpression>
        get() = KtDotQualifiedExpression::class.java

    override val referenceProvider: ReferenceProvider<KtDotQualifiedExpression>
        get() = ReferenceProvider { dotExpression ->
            if (!isCatalogReferenceCandidate(dotExpression)) return@ReferenceProvider emptyList()
            val catalogFile = findCatalogFile(dotExpression) ?: return@ReferenceProvider emptyList()
            listOf(KtVersionCatalogReference(dotExpression, catalogFile))
        }

    private fun isCatalogReferenceCandidate(dotExpression: KtDotQualifiedExpression): Boolean =
        dotExpression.containingFile.name.endsWith(".gradle.kts")
                && dotExpression.matchesTopmostCatalogReferencePattern()

    private fun findCatalogFile(dotExpression: KtDotQualifiedExpression): VirtualFile? {
        val module = dotExpression.module ?: return null
        val catalogName = dotExpression.text.substringBefore(".")
        return getVersionCatalogFiles(module)[catalogName]
    }
}