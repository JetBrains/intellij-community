// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isDefinitelyNotNullType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.types.typeUtil.makeNullable

class CastExpressionFix(element: KtExpression, type: KotlinType) : KotlinQuickFixAction<KtExpression>(element), LowPriorityAction {
    private val typePresentation = IdeDescriptorRenderers.SOURCE_CODE_TYPES_WITH_SHORT_NAMES.renderType(type)
    private val typeSourceCode = IdeDescriptorRenderers.SOURCE_CODE_TYPES.renderType(type).let {
        if (type.isDefinitelyNotNullType) "($it)" else it
    }

    private val upOrDownCast: Boolean = run {
        val expressionType = element.analyze(BodyResolveMode.PARTIAL).getType(element)
        expressionType != null && (type.isSubtypeOf(expressionType) || expressionType.isSubtypeOf(type))
                && expressionType != type.makeNullable() //covered by AddExclExclCallFix
    }

    override fun getFamilyName() = KotlinBundle.message("fix.cast.expression.family")
    override fun getText() = element?.let { KotlinBundle.message("fix.cast.expression.text", it.text, typePresentation) } ?: ""

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile) = upOrDownCast

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val expressionToInsert = KtPsiFactory(project).createExpressionByPattern("$0 as $1", element, typeSourceCode)
        val newExpression = element.replaced(expressionToInsert)
        ShortenReferences.DEFAULT.process((KtPsiUtil.safeDeparenthesize(newExpression) as KtBinaryExpressionWithTypeRHS).right!!)
        editor?.caretModel?.moveToOffset(newExpression.endOffset)
    }

    abstract class Factory : KotlinSingleIntentionActionFactoryWithDelegate<KtExpression, KotlinType>(IntentionActionPriority.LOW) {
        override fun getElementOfInterest(diagnostic: Diagnostic) = diagnostic.psiElement as? KtExpression
        override fun createFix(originalElement: KtExpression, data: KotlinType) = CastExpressionFix(originalElement, data)
    }

    object SmartCastImpossibleFactory : Factory() {
        override fun extractFixData(element: KtExpression, diagnostic: Diagnostic) = Errors.SMARTCAST_IMPOSSIBLE.cast(diagnostic).a
    }

    object GenericVarianceConversion : Factory() {
        override fun extractFixData(element: KtExpression, diagnostic: Diagnostic): KotlinType {
            return ErrorsJvm.JAVA_TYPE_MISMATCH.cast(diagnostic).b.asFlexibleType().upperBound
        }
    }
}
