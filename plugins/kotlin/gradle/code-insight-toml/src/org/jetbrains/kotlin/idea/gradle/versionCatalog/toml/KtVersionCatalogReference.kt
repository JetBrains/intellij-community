// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.plugins.gradle.service.resolve.findVersionCatalogEntryElement

class KtVersionCatalogReference(
    val refExpr: KtDotQualifiedExpression,
    val catalogFile: VirtualFile
) : PsiReferenceBase<KtDotQualifiedExpression>(refExpr) {
    override fun resolve(): PsiElement? {
        val psiFile = refExpr.manager.findFile(catalogFile) ?: return null
        val withoutCatalogName = refExpr.text.substringAfter(".") // libs.versions.junit -> versions.junit
        return findVersionCatalogEntryElement(psiFile, withoutCatalogName)
    }
}