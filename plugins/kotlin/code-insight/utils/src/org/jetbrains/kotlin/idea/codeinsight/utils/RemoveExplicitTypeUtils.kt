// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.idea.base.psi.setCallableTypeReference
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Removes the explicitly declared type of this declaration if it exists.
 */
fun KtDeclaration.removeDeclarationTypeReference() {
    if (this is KtCallableDeclaration) {
        setCallableTypeReference(null)
    } else if (this is KtPropertyAccessor) {
        val first = parameterList?.nextSibling ?: return
        val last = typeReference ?: return
        deleteChildRange(first, last)
    }
}

fun KtLambdaExpression.removeExplicitParameterTypes() {
    val oldParameterList = functionLiteral.valueParameterList ?: return
    if (oldParameterList.parameters.none { it.typeReference != null }) return

    val parameterString = oldParameterList.parameters.joinToString(", ") {
        it.destructuringDeclaration?.text ?: it.name.orEmpty()
    }

    val newParameterList = KtPsiFactory(project).createLambdaParameterList(parameterString)
    oldParameterList.replace(newParameterList)
}
