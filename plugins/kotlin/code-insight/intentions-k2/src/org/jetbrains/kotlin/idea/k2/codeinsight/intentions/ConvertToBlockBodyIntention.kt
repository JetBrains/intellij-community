// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.adjustLineIndent
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance

internal class ConvertToBlockBodyIntention :
    AbstractKotlinApplicableIntentionWithContext<KtDeclarationWithBody, ConvertToBlockBodyIntention.Context>(KtDeclarationWithBody::class) {

    class Context(
        val returnTypeIsUnit: Boolean,
        val returnTypeIsNothing: Boolean,
        val returnTypeString: String,
        val returnTypeClassId: ClassId?,
        val bodyTypeIsUnit: Boolean,
        val bodyTypeIsNothing: Boolean,
        val reformat: Boolean,
    )

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.block.body")
    override fun getActionName(element: KtDeclarationWithBody, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclarationWithBody> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtDeclarationWithBody): Boolean =
        (element is KtNamedFunction || element is KtPropertyAccessor) && !element.hasBlockBody() && element.hasBody()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtDeclarationWithBody): Context? {
        if (element is KtNamedFunction) {
            val returnType = element.getReturnKtType()
            if (!element.hasDeclaredReturnType() && returnType is KtClassErrorType) return null
        }
        return createContextForDeclaration(element, reformat = true)
    }

    override fun apply(element: KtDeclarationWithBody, context: Context, project: Project, editor: Editor?) {
        val body = element.bodyExpression ?: return

        val newBody = when (element) {
            is KtNamedFunction -> {
                if (!element.hasDeclaredReturnType() && !context.returnTypeIsUnit) {
                    element.setType(context.returnTypeString, context.returnTypeClassId)
                }
                generateBody(body, context, returnsValue = !context.returnTypeIsUnit && !context.returnTypeIsNothing)
            }

            is KtPropertyAccessor -> {
                val parent = element.parent
                if (parent is KtProperty && parent.typeReference == null) {
                    parent.setType(context.returnTypeString, context.returnTypeClassId)
                }

                generateBody(body, context, element.isGetter)
            }

            else -> throw RuntimeException("Unknown declaration type: $element")
        }

        element.equalsToken?.delete()
        val replaced = body.replace(newBody)
        if (context.reformat) element.containingKtFile.adjustLineIndent(replaced.startOffset, replaced.endOffset)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) =
        element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)
}

private fun KtAnalysisSession.createContextForDeclaration(
    declaration: KtDeclarationWithBody,
    reformat: Boolean
): ConvertToBlockBodyIntention.Context? {
    val body = declaration.bodyExpression ?: return null
    val returnType = declaration.getReturnKtType().approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = true)
    val bodyType = body.getKtType() ?: return null
    return ConvertToBlockBodyIntention.Context(
        returnType.isUnit,
        returnType.isNothing,
        returnType.render(position = Variance.OUT_VARIANCE),
        returnType.expandedClassSymbol?.classIdIfNonLocal,
        bodyType.isUnit,
        bodyType.isNothing,
        reformat,
    )
}

private fun generateBody(body: KtExpression, context: ConvertToBlockBodyIntention.Context, returnsValue: Boolean): KtExpression {
    val factory = KtPsiFactory(body.project)
    if (context.bodyTypeIsUnit && body is KtNameReferenceExpression) return factory.createEmptyBody()
    val needReturn = returnsValue && (!context.bodyTypeIsUnit && !context.bodyTypeIsNothing)
    return if (needReturn) {
        val annotatedExpr = body as? KtAnnotatedExpression
        val returnedExpr = annotatedExpr?.baseExpression ?: body
        val block = factory.createSingleStatementBlock(factory.createExpressionByPattern("return $0", returnedExpr))
        val statement = block.firstStatement
        annotatedExpr?.annotationEntries?.forEach {
            block.addBefore(it, statement)
            block.addBefore(factory.createNewLine(), statement)
        }
        block
    } else {
        factory.createSingleStatementBlock(body)
    }
}

private fun KtCallableDeclaration.setType(typeString: String, classId: ClassId?) {
    val addedTypeReference = setTypeReference(KtPsiFactory(project).createType(typeString))
    if (classId != null && addedTypeReference != null) {
        shortenReferences(addedTypeReference)
    }
}
