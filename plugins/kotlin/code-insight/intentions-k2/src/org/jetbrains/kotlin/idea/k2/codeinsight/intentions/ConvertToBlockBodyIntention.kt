// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsight.utils.adjustLineIndent
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class ConvertToBlockBodyIntention :
    AbstractKotlinApplicatorBasedIntention<KtDeclarationWithBody, ConvertToBlockBodyIntention.Input>(KtDeclarationWithBody::class) {

    class Input(
        val returnTypeIsUnit: Boolean,
        val returnTypeIsNothing: Boolean,
        val returnTypeString: String,
        val returnTypeClassId: ClassId?,
        val bodyTypeIsUnit: Boolean,
        val bodyTypeIsNothing: Boolean,
        val reformat: Boolean,
    ) : KotlinApplicatorInput

    override fun getApplicator() = applicator<KtDeclarationWithBody, Input> {
        familyAndActionName(KotlinBundle.lazyMessage(("convert.to.block.body")))
        isApplicableByPsi { (it is KtNamedFunction || it is KtPropertyAccessor) && !it.hasBlockBody() && it.hasBody() }
        applyTo { declaration, input ->
            val body = declaration.bodyExpression!!

            val newBody = when (declaration) {
                is KtNamedFunction -> {
                    if (!declaration.hasDeclaredReturnType() && !input.returnTypeIsUnit) {
                        declaration.setType(input.returnTypeString, input.returnTypeClassId)
                    }
                    generateBody(body, input, !input.returnTypeIsUnit && !input.returnTypeIsNothing)
                }

                is KtPropertyAccessor -> {
                    val parent = declaration.parent
                    if (parent is KtProperty && parent.typeReference == null) {
                        parent.setType(input.returnTypeString, input.returnTypeClassId)
                    }

                    generateBody(body, input, declaration.isGetter)
                }

                else -> throw RuntimeException("Unknown declaration type: $declaration")
            }

            declaration.equalsToken!!.delete()
            val replaced = body.replace(newBody)
            if (input.reformat) declaration.containingKtFile.adjustLineIndent(replaced.startOffset, replaced.endOffset)
        }
    }


    override fun skipProcessingFurtherElementsAfter(element: PsiElement) =
        element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun getInputProvider() = inputProvider { psi: KtDeclarationWithBody ->
        if (psi is KtNamedFunction) {
            val returnType = psi.getReturnKtType()
            if (!psi.hasDeclaredReturnType() && returnType is KtClassErrorType) return@inputProvider null
        }
        createInputForDeclaration(psi, true)
    }
}

private fun KtAnalysisSession.createInputForDeclaration(
    declaration: KtDeclarationWithBody,
    reformat: Boolean
): ConvertToBlockBodyIntention.Input? {
    val body = declaration.bodyExpression ?: return null
    val returnType = declaration.getReturnKtType().approximateToSuperPublicDenotableOrSelf()
    val bodyType = body.getKtType() ?: return null
    return ConvertToBlockBodyIntention.Input(
        returnType.isUnit,
        returnType.isNothing,
        returnType.render(),
        returnType.expandedClassSymbol?.classIdIfNonLocal,
        bodyType.isUnit,
        bodyType.isNothing,
        reformat,
    )
}

private fun generateBody(body: KtExpression, input: ConvertToBlockBodyIntention.Input, returnsValue: Boolean): KtExpression {
    val factory = KtPsiFactory(body)
    if (input.bodyTypeIsUnit && body is KtNameReferenceExpression) return factory.createEmptyBody()
    val needReturn = returnsValue && (!input.bodyTypeIsUnit && !input.bodyTypeIsNothing)
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
