// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.OptInAnnotationWrongTargetFixUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object OptInAnnotationWrongTargetFixFactory {

    val optInAnnotationWrongTargetFixFactory =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInMarkerOnWrongTarget ->
            val annotationEntry = diagnostic.psi.safeAs<KtAnnotationEntry>()
                ?: return@IntentionBased emptyList()

            val annotatedElement = annotationEntry.getParentOfTypes(
                strict = true,
                KtProperty::class.java,
                KtParameter::class.java,
            ) ?: return@IntentionBased emptyList()

            val userType = annotationEntry.typeReference?.typeElement as? KtUserType
                ?: return@IntentionBased emptyList()

            val resolvedKtClass =
                userType.referenceExpression?.mainReference?.resolve() as? KtClass
                    ?: return@IntentionBased emptyList()

            val annotationClassId = resolvedKtClass.classIdIfNonLocal
                ?: return@IntentionBased emptyList()

            OptInAnnotationWrongTargetFixUtils.collectQuickFixes(annotatedElement, annotationEntry, annotationClassId)
        }
}
