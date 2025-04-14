// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix
import org.jetbrains.kotlin.idea.quickfix.WrapWithArrayLiteralFix
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.Variance
import java.util.*

internal object ArgumentTypeMismatchFactory {

    @OptIn(KaExperimentalApi::class)
    val addArrayOfTypeFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        if (!isQuickFixAvailable(diagnostic)) return@ModCommandBased emptyList()

        val prefix = if (diagnostic.expectedType.isPrimitiveArray) {
            val typeName = diagnostic.expectedType.render(
                renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
                position = Variance.INVARIANT,
            )
            "${typeName.replaceFirstChar { it.lowercase(Locale.US) }}Of"
        } else {
            "arrayOf"
        }

        listOf(
            AddArrayOfTypeFix(expression, prefix)
        )
    }

    val wrapWithArrayLiteralFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        if (!isQuickFixAvailable(diagnostic)) return@ModCommandBased emptyList()

        listOf(
            WrapWithArrayLiteralFix(expression)
        )
    }

    private fun KaSession.isQuickFixAvailable(diagnostic: KaFirDiagnostic.ArgumentTypeMismatch): Boolean {
        if (PsiTreeUtil.getParentOfType(diagnostic.psi, KtAnnotationEntry::class.java) == null) return false
        val expectedType = diagnostic.expectedType
        val arrayElementType = expectedType.arrayElementType
        return expectedType.isPrimitiveArray || (arrayElementType != null && diagnostic.actualType.isSubtypeOf(arrayElementType))
    }

    private val KaType.isPrimitiveArray: Boolean
        get() {
            return this is KaClassType && StandardClassIds.elementTypeByPrimitiveArrayType.containsKey(classId)
        }
}
