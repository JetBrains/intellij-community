// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

class ConvertArrayParameterToVarargIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java, KotlinBundle.messagePointer("convert.to.vararg.parameter")
) {
    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        val typeReference = element.getChildOfType<KtTypeReference>() ?: return false
        if (element.isLambdaParameter) return false
        if (element.isVarArg) return false

        val type = element.descriptor?.type ?: return false
        return when {
            KotlinBuiltIns.isPrimitiveArray(type) -> {
                setTextGetter(defaultTextGetter)
                true
            }
            KotlinBuiltIns.isArray(type) -> {
                val typeArgument = typeReference.typeElement?.typeArgumentsAsTypes?.firstOrNull()
                val typeProjection = typeArgument?.parent as? KtTypeProjection
                if (typeProjection?.hasModifier(KtTokens.IN_KEYWORD) == false) {
                    setTextGetter(
                        if (!typeProjection.hasModifier(KtTokens.OUT_KEYWORD) && !KotlinBuiltIns.isPrimitiveType(
                                element.builtIns.getArrayElementType(
                                    type
                                )
                            )
                        ) {
                            KotlinBundle.messagePointer("0.may.break.code", defaultText)
                        } else {
                            defaultTextGetter
                        }
                    )
                    true
                } else {
                    false
                }
            }
            else ->
                false
        }
    }

    override fun applyTo(element: KtParameter, editor: Editor?) {
        val typeReference = element.getChildOfType<KtTypeReference>() ?: return
        val type = element.descriptor?.type ?: return
        val newType = KotlinBuiltIns.getPrimitiveArrayElementType(type)?.typeName?.asString()
            ?: typeReference.typeElement?.typeArgumentsAsTypes?.firstOrNull()?.text
            ?: return

        typeReference.replace(KtPsiFactory(element.project).createType(newType))
        element.addModifier(KtTokens.VARARG_KEYWORD)
    }
}