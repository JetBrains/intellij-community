// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.quickfix.OptInAnnotationWrongTargetFixUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object OptInAnnotationWrongTargetFixFactory {
    val optInAnnotationWrongTargetFixFactory =
        diagnosticFixFactory(KtFirDiagnostic.OptInMarkerOnWrongTarget::class) { diagnostic ->
            val annotationEntry = diagnostic.psi.safeAs<KtAnnotationEntry>() ?: return@diagnosticFixFactory emptyList()
            val annotatedElement = annotationEntry.getParentOfTypes(
                strict = true,
                KtProperty::class.java,
                KtParameter::class.java,
            ) ?: return@diagnosticFixFactory emptyList()

            val userType = annotationEntry.typeReference?.typeElement as? KtUserType ?: return@diagnosticFixFactory emptyList()
            val resolvedKtClass =
                userType.referenceExpression?.mainReference?.resolve() as? KtClass ?: return@diagnosticFixFactory emptyList()
            val annotationClassId = resolvedKtClass.classIdIfNonLocal ?: return@diagnosticFixFactory emptyList()

            return@diagnosticFixFactory OptInAnnotationWrongTargetFixUtils.collectQuickFixes(annotatedElement, annotationEntry, annotationClassId)
        }
}
