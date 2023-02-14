// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isArrayOrNullableArray
import org.jetbrains.kotlin.types.isNullable

class ConvertToIsArrayOfCallFix(element: KtIsExpression, lhsType: KotlinType, arrayArgumentType: KotlinType) :
  KotlinQuickFixAction<KtIsExpression>(element) {

    private val lhsIsNullable = lhsType.isNullable()

    private val lhsIsArray = lhsType.isArrayOrNullableArray()

    private val arrayArgumentTypeText = IdeDescriptorRenderers.SOURCE_CODE_TYPES.renderType(arrayArgumentType)

    override fun getFamilyName() = KotlinBundle.message("fix.convert.to.is.array.of.call")

    override fun getText() = familyName

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val isExpression = element ?: return
        val isNegated = isExpression.isNegated
        val isArrayOfCall = "isArrayOf<$arrayArgumentTypeText>()"
        val newPattern = when {
            lhsIsArray && !lhsIsNullable ->
                if (isNegated) "!$0.$isArrayOfCall" else "$0.$isArrayOfCall"
            lhsIsArray && lhsIsNullable ->
                if (isNegated) "$0?.$isArrayOfCall != true" else "$0?.$isArrayOfCall == true"
            else ->
                if (isNegated) "!($0 is Array<*> && $0.$isArrayOfCall)" else "$0 is Array<*> && $0.$isArrayOfCall"
        }
        val replaced = isExpression.replaced(KtPsiFactory(project).createExpressionByPattern(newPattern, isExpression.leftHandSide))
        ShortenReferences.DEFAULT.process(replaced)
    }

    companion object : KotlinSingleIntentionActionFactory() {
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
            return ConvertToIsArrayOfCallFix(parentIsExpression, lhsType, arrayArgumentType)
        }
    }
}