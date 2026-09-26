// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.versionCatalog.toml

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

internal fun KtDotQualifiedExpression.hasWrappingVersionCatalogExpression() =
    parent is KtDotQualifiedExpression
            && parent.lastChild is KtNameReferenceExpression // this is false if parent expression ends with `.get()`

/**
 * Finds the topmost wrapping KtDotQualifiedExpression that could be a version catalog reference.
 * Example:
 *  - Given: `aaa` element from `libs.aaa.b.c`. Returns: `libs.aaa.b.c` KtDotQualifiedExpression
 *  - Given: `aaa` element from `libs.aaa.b.c.get()`. Returns: `libs.aaa.b.c` (before `.get()`).
 */
internal fun findTopmostVersionCatalogExpression(element: PsiElement?): KtDotQualifiedExpression? {
    var fullExpression = element?.parentOfType<KtDotQualifiedExpression>() ?: return null
    while (fullExpression.hasWrappingVersionCatalogExpression()) {
        fullExpression = fullExpression.parent as KtDotQualifiedExpression
    }
    return fullExpression
}

fun KtDotQualifiedExpression.matchesTopmostCatalogReferencePattern(): Boolean =
    this.hasOnlyNameReferences() && !hasWrappingVersionCatalogExpression()

private fun KtDotQualifiedExpression.hasOnlyNameReferences(): Boolean =
    this.children.all {
        when (it) {
            is KtNameReferenceExpression -> true
            is KtDotQualifiedExpression -> it.hasOnlyNameReferences()
            else -> false
        }
    }