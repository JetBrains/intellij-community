// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

fun KtCallableDeclaration.setTypeReference(typeString: String, shortenReferences: Boolean = true) {
    val typeReference = KtPsiFactory(project).createType(typeString)
    val addedTypeReference = setTypeReference(typeReference)
    if (shortenReferences && addedTypeReference != null) {
        ShortenReferencesFacility.getInstance().shorten(addedTypeReference)
    }
}

@OptIn(KaExperimentalApi::class)
context(KaSession)
fun KtCallableDeclaration.setType(type: KaType, shortenReferences: Boolean = true) {
    if (type is KaErrorType) return
    val renderedType = type.render(position = Variance.OUT_VARIANCE)
    setTypeReference(renderedType, shortenReferences)
}