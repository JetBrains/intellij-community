// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.plugins.gradle.toml.findTomlCatalogKey
import org.toml.lang.psi.TomlFile

class KtTomlVersionCatalogReference(
    val refExpr: KtDotQualifiedExpression,
    val file: TomlFile
) : PsiReferenceBase<KtDotQualifiedExpression>(refExpr) {
    override fun resolve(): PsiElement? {
        val withoutCatalogName = refExpr.text.substringAfter(".") // libs.versions.junit -> versions.junit
        return findTomlCatalogKey(file, withoutCatalogName)
    }
}