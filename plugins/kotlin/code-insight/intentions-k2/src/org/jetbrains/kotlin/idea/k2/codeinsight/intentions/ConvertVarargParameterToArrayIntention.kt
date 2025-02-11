// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue

internal class ConvertVarargParameterToArrayIntention :
    KotlinApplicableModCommandAction<KtParameter, ConvertVarargParameterToArrayIntention.Context>(KtParameter::class) {


    data class Context(val classId: ClassId)

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("convert.to.array.parameter")
    }

    context(KaSession) override fun prepareContext(element: KtParameter): Context? {
        if (element.typeReference == null) return null
        val typeRef = element.typeReference
        if (element.isVarArg) {
            val elementType = typeRef?.type ?: return null
            val classId = elementType.symbol?.classId ?: return null
            return Context(classId)
        }
        return null
    }

    override fun invoke(actionContext: ActionContext, element: KtParameter, elementContext: Context, updater: ModPsiUpdater) {
        val typeReference = element.typeReference ?: return
        val factory = KtPsiFactory(actionContext.project)
        val isPrimitiveArray = StandardClassIds.elementTypeByPrimitiveArrayType.contains(elementContext.classId)
        val newType = isPrimitiveArray.ifTrue {
            elementContext.classId.shortClassName.identifier
        } ?: "Array<${typeReference.text}>"
        typeReference.replace(factory.createType(newType))
        element.removeModifier(KtTokens.VARARG_KEYWORD)
    }
}