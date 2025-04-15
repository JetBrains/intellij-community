// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.types.Variance

internal class ConvertArrayParameterToVarargIntention :
    KotlinApplicableModCommandAction<KtParameter, KtTypeReference>(KtParameter::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.vararg.parameter")

    override fun isApplicableByPsi(element: KtParameter): Boolean =
        !element.isLambdaParameter && !element.isVarArg && !element.isFunctionTypeParameter

    override fun getPresentation(context: ActionContext, element: KtParameter): Presentation? = analyze(element) {
        val typeReference = element.getChildOfType<KtTypeReference>() ?: return null
        val symbol = element.symbol as? KaValueParameterSymbol ?: return null
        val type = symbol.returnType as? KaClassType? ?: return null
        val actionName = when {
            type.isPrimitiveArray -> familyName
            type.classId == StandardClassIds.Array -> {
                val typeArgument = typeReference.typeElement?.typeArgumentsAsTypes?.firstOrNull()
                val typeProjection = typeArgument?.parent as? KtTypeProjection
                if (typeProjection?.hasModifier(KtTokens.IN_KEYWORD) != false) return null
                if (!typeProjection.hasModifier(KtTokens.OUT_KEYWORD) &&
                    type.arrayElementType?.isPrimitive == false
                ) {
                    KotlinBundle.message("0.may.break.code", familyName)
                }
                else {
                    familyName
                }
            }

            else -> return null
        }
        return Presentation.of(actionName)
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtParameter): KtTypeReference? {
        val symbol = element.symbol as? KaValueParameterSymbol ?: return null
        val elementType = symbol.returnType.arrayElementType ?: return null
        val newType = elementType.withNullability(KaTypeNullability.NON_NULLABLE).render(position = Variance.IN_VARIANCE)
        return KtPsiFactory(element.project).createType(newType)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtParameter,
        elementContext: KtTypeReference,
        updater: ModPsiUpdater,
    ) {
        val typeReference = element.getChildOfType<KtTypeReference>() ?: return
        shortenReferences(typeReference.replace(elementContext) as KtTypeReference)
        element.addModifier(KtTokens.VARARG_KEYWORD)
    }
}

private val KaClassType.isPrimitiveArray: Boolean
    get() {
        return StandardClassIds.elementTypeByPrimitiveArrayType.containsKey(classId)
    }
