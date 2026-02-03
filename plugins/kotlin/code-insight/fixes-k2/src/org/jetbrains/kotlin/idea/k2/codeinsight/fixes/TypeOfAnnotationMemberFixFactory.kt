// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.TypeOfAnnotationMemberFix
import org.jetbrains.kotlin.psi.KtTypeReference

internal object TypeOfAnnotationMemberFixFactory {

  val typeOfAnnotationMemberFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InvalidTypeOfAnnotationMember ->
    val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()

    val arrayElementType = typeReference.type.arrayElementType ?: return@ModCommandBased emptyList()
    if (!arrayElementType.isPrimitive) return@ModCommandBased emptyList()

    val classId = (arrayElementType as KaClassType).classId
    val fixedArrayTypeText = "${classId.shortClassName}Array"

    listOf(
      TypeOfAnnotationMemberFix(typeReference, fixedArrayTypeText)
    )
  }
}
