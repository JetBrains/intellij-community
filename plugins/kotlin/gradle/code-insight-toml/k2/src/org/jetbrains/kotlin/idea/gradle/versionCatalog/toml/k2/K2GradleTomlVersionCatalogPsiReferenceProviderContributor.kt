// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.k2

import org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.KtTomlVersionCatalogReference
import org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.matchesTopmostCatalogReferencePattern
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.references.KotlinPsiReferenceProviderContributor
import org.jetbrains.kotlin.references.KotlinPsiReferenceProviderContributor.ReferenceProvider
import org.jetbrains.plugins.gradle.toml.findTomlFile

internal class K2GradleTomlVersionCatalogPsiReferenceProviderContributor : KotlinPsiReferenceProviderContributor<KtDotQualifiedExpression> {
    override val elementClass: Class<KtDotQualifiedExpression>
        get() = KtDotQualifiedExpression::class.java

    override val referenceProvider: ReferenceProvider<KtDotQualifiedExpression>
        get() = ReferenceProvider { dotExpression ->
            val tomlFile = when {
                !dotExpression.containingFile.name.endsWith(".gradle.kts") -> null
                !dotExpression.matchesTopmostCatalogReferencePattern() -> null
                else -> {
                    val catalogName = dotExpression.text.substringBefore(".")
                    findTomlFile(dotExpression, catalogName)
                }
            }

            listOfNotNull(tomlFile?.let { KtTomlVersionCatalogReference(dotExpression, it) })
        }
}
