// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isArrayOrNullableArray
import org.jetbrains.kotlin.types.isNullable

object ConvertToIsArrayOfCallFixFactory : KotlinSingleIntentionActionFactory()  {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val casted = Errors.CANNOT_CHECK_FOR_ERASED.cast(diagnostic)
        val element = casted.psiElement
        if (!element.platform.isJvm()) return null
        val parentIsExpression = element.parent as? KtIsExpression ?: return null
        val type = casted.a
        if (!type.isArrayOrNullableArray()) return null
        val arrayArgumentType = type.arguments.singleOrNull()?.type ?: return null
        val context = parentIsExpression.analyze(BodyResolveMode.PARTIAL)
        val lhsType = parentIsExpression.leftHandSide.getType(context) ?: return null
        val lhsIsNullable = lhsType.isNullable()
        val lhsIsArray = lhsType.isArrayOrNullableArray()
        val arrayArgumentTypeText = IdeDescriptorRenderers.SOURCE_CODE_TYPES.renderType(arrayArgumentType)

        return ConvertToIsArrayOfCallFix(
            element = parentIsExpression,
            lhsIsNullable = lhsIsNullable,
            lhsIsArray = lhsIsArray,
            arrayArgumentTypeText = arrayArgumentTypeText
        ).asIntention()
    }
}