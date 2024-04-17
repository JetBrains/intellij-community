// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries

import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * A simple [AddKotlinLibraryQuickFixProvider] that determines if an unresolved reference belongs to the library if they
 * are contained in the [namesToCheck].
 * The names that are checked are references or function calls that are not part of a dot qualified expression.
 */
abstract class SimpleAddKotlinLibraryQuickFixProvider(
    libraryGroupId: String,
    libraryArtifactId: String,
    private val namesToCheck: Set<String>,
) : AddKotlinLibraryQuickFixProvider(libraryGroupId, libraryArtifactId) {
    override fun isLibraryReference(ref: PsiReference): Boolean {
        val referenceExpression = ref.element as? KtReferenceExpression ?: return false
        if (!namesToCheck.contains(referenceExpression.text)) return false
        return referenceExpression.parent !is KtQualifiedExpression
    }
}