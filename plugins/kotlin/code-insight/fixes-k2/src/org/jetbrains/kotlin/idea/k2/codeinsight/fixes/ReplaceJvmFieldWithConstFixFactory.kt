// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.checkMayBeConstantByFields
import org.jetbrains.kotlin.idea.quickfix.ReplaceJvmFieldWithConstFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ReplaceJvmFieldWithConstFixFactory {
    private fun KaSession.createQuickFix(annotation: KtAnnotationEntry): ReplaceJvmFieldWithConstFix? {
        val property = annotation.getParentOfType<KtProperty>(false) ?: return null
        val initializer = property.initializer ?: return null
        if (!property.checkMayBeConstantByFields()) return null

        val returnType = property.returnType
        if (returnType.isMarkedNullable) return null
        if (!returnType.isPrimitive && !returnType.isStringType) return null

        if (initializer.evaluate() == null) {
            return null
        }

        return ReplaceJvmFieldWithConstFix(annotation)
    }

    val inapplicableJvmField = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InapplicableJvmField ->
        listOfNotNull(createQuickFix(diagnostic.psi))
    }
}