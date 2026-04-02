// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

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
        typeReference = null
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
