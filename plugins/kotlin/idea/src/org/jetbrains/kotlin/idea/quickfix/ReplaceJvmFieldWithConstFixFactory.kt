// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.checkers.ConstModifierChecker
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal object ReplaceJvmFieldWithConstFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val annotation = diagnostic.psiElement as? KtAnnotationEntry ?: return null
        val property = annotation.getParentOfType<KtProperty>(false) ?: return null
        val propertyDescriptor = property.descriptor as? PropertyDescriptor ?: return null
        if (!ConstModifierChecker.canBeConst(property, property, propertyDescriptor)) {
            return null
        }

        val initializer = property.initializer ?: return null
        if (!initializer.isConstantExpression()) {
            return null
        }

        return ReplaceJvmFieldWithConstFix(annotation).asIntention()
    }

    private fun KtExpression.isConstantExpression() =
        ConstantExpressionEvaluator.getConstant(this, analyze(BodyResolveMode.PARTIAL))?.let { !it.usesNonConstValAsConstant } ?: false
}