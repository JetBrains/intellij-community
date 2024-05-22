// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.TypeOfAnnotationMemberFix

internal object TypeOfAnnotationMemberFixFactory {

  val typeOfAnnotationMemberFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.InvalidTypeOfAnnotationMember ->
    val typeReference = diagnostic.psi

    val arrayElementType = typeReference.getKtType().getArrayElementType() ?: return@ModCommandBased emptyList()
    if (!arrayElementType.isPrimitive) return@ModCommandBased emptyList()

    val classId = (arrayElementType as KtNonErrorClassType).classId
    val fixedArrayTypeText = "${classId.shortClassName}Array"

    listOf(
      TypeOfAnnotationMemberFix(typeReference, fixedArrayTypeText)
    )
  }
}
