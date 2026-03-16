// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.CAST_NEVER_SUCCEEDS
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.typeBinding.createTypeBinding
import org.jetbrains.kotlin.types.typeUtil.isPrimitiveNumberType

internal object ReplacePrimitiveCastWithNumberConversionFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = CAST_NEVER_SUCCEEDS.cast(diagnostic).psiElement as? KtOperationReferenceExpression ?: return null
        val binaryExpression = element.parent as? KtBinaryExpressionWithTypeRHS ?: return null

        val context = binaryExpression.analyze()

        val expressionType = binaryExpression.left.getType(context) ?: return null
        if (!expressionType.isPrimitiveNumberType()) return null

        val castType = binaryExpression.right?.createTypeBinding(context)?.type ?: return null
        if (!castType.isPrimitiveNumberType()) return null

        return ReplacePrimitiveCastWithNumberConversionFix(
            binaryExpression,
            SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(castType)
        ).asIntention()
    }
}