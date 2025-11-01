/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.k2

import org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.KtTomlVersionCatalogReference
import org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.matchesTopmostCatalogReferencePattern
import org.jetbrains.kotlin.idea.references.KotlinPsiReferenceRegistrar
import org.jetbrains.kotlin.idea.references.KotlinReferenceProviderContributor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.plugins.gradle.toml.findTomlFile

// Enables providing references in build.gradle.kts files for TOML version catalogs.
// This code is a changed implementation copied from com/android/tools/idea/gradle/catalog/KtsCatalogReferenceProviders.kt
// TODO avoid accessing internal class KotlinFirReferenceContributor.
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
internal class K2GradleTomlVersionCatalogReferenceProviderContributor : KotlinReferenceProviderContributor {
    private val contributor = org.jetbrains.kotlin.analysis.api.fir.references.KotlinFirReferenceContributor()
    override fun registerReferenceProviders(registrar: KotlinPsiReferenceRegistrar) {
        contributor.registerReferenceProviders(registrar)
        registerProvider(registrar)
    }
}

private fun registerProvider(registrar: KotlinPsiReferenceRegistrar) {
    registrar.registerProvider<KtDotQualifiedExpression> provider@{ dotExpression: KtDotQualifiedExpression ->
        if (!dotExpression.containingFile.name.endsWith(".gradle.kts")) return@provider null
        if (!dotExpression.matchesTopmostCatalogReferencePattern()) return@provider null

        val catalogName = dotExpression.text.substringBefore(".")
        val file = findTomlFile(dotExpression, catalogName) ?: return@provider null
        return@provider KtTomlVersionCatalogReference(dotExpression, file)
    }
}
