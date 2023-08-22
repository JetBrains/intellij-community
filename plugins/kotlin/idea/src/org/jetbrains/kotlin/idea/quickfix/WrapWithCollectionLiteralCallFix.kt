// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class WrapWithCollectionLiteralCallFix private constructor(
    element: KtExpression,
    private val functionName: String,
    private val wrapInitialElement: Boolean
) : KotlinQuickFixAction<KtExpression>(element) {

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val psiFactory = KtPsiFactory(project)

        val replaced =
            if (wrapInitialElement)
                expression.replaced(psiFactory.createExpressionByPattern("$functionName($0)", expression))
            else
                expression.replaced(psiFactory.createExpression("$functionName()"))

        editor?.caretModel?.moveToOffset(replaced.endOffset)
    }

    override fun getFamilyName(): String = KotlinBundle.message("wrap.with.collection.literal.call")
    override fun getText() =
        if (wrapInitialElement)
            KotlinBundle.message("wrap.element.with.0.call", functionName)
        else
            KotlinBundle.message("replace.with.0.call", functionName)

    companion object {
        fun create(expectedType: KotlinType, expressionType: KotlinType, element: KtExpression): List<WrapWithCollectionLiteralCallFix> {
            if (element.getStrictParentOfType<KtAnnotationEntry>() != null) return emptyList()

            val collectionType =
                with(ConvertCollectionFix) {
                    expectedType.getCollectionType(acceptNullableTypes = true)
                } ?: return emptyList()

            val expectedArgumentType =
                expectedType
                    .arguments.singleOrNull()
                    ?.takeIf { it.projectionKind != Variance.IN_VARIANCE }
                    ?.type
                    ?: return emptyList()

            val result = mutableListOf<WrapWithCollectionLiteralCallFix>()

            val isNullExpression = element.isNullExpression()
            if ((expressionType.isSubtypeOf(expectedArgumentType) || isNullExpression) && collectionType.literalFunctionName != null) {
                result += WrapWithCollectionLiteralCallFix(element, collectionType.literalFunctionName, wrapInitialElement = true)
            }

            // Replace "null" with emptyList()
            if (isNullExpression && collectionType.emptyCollectionFunction != null) {
                result += WrapWithCollectionLiteralCallFix(element, collectionType.emptyCollectionFunction, wrapInitialElement = false)
            }

            return result
        }
    }

}
