// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSignedOrUnsignedNumberType

class NumberConversionFix(
    element: KtExpression,
    type: KotlinType,
    private val disableIfAvailable: IntentionAction? = null
) : KotlinQuickFixAction<KtExpression>(element) {
    private val isConversionAvailable: Boolean = run {
        val expressionType = element.analyze(BodyResolveMode.PARTIAL).getType(element)
        expressionType != null && expressionType != type &&
                expressionType.isSignedOrUnsignedNumberType() && type.isSignedOrUnsignedNumberType()
    }
    private val typePresentation = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(type)

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile) =
        disableIfAvailable?.isAvailable(project, editor, file) != true && isConversionAvailable

    override fun getFamilyName() = KotlinBundle.message("insert.number.conversion")
    override fun getText() = KotlinBundle.message("convert.expression.to.0", typePresentation)

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val expressionToInsert = KtPsiFactory(file).createExpressionByPattern("$0.to$1()", element, typePresentation)
        val newExpression = element.replaced(expressionToInsert)
        editor?.caretModel?.moveToOffset(newExpression.endOffset)
    }
}