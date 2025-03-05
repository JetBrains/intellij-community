// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RemoveNullableFix(element: KtNullableType, private val typeOfError: NullableKind) :
    PsiUpdateModCommandAction<KtNullableType>(element) {
    enum class NullableKind(@Nls val message: String) {
        REDUNDANT(KotlinBundle.message("remove.redundant")),
        SUPERTYPE(KotlinBundle.message("text.remove.question")),
        USELESS(KotlinBundle.message("remove.useless")),
        PROPERTY(KotlinBundle.message("make.not.nullable"))
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("text.remove.question")

    override fun getPresentation(context: ActionContext, element: KtNullableType): Presentation = Presentation.of(typeOfError.message)

    override fun invoke(context: ActionContext, element: KtNullableType, updater: ModPsiUpdater) {
        val type = element.innerType ?: error("No inner type ${element.text}, should have been rejected in createFactory()")
        element.replace(type)
    }

    companion object {
        val removeForRedundant: QuickFixesPsiBasedFactory<KtElement> = createFactory(NullableKind.REDUNDANT)
        val removeForSuperType: QuickFixesPsiBasedFactory<KtElement> = createFactory(NullableKind.SUPERTYPE)
        val removeForUseless: QuickFixesPsiBasedFactory<KtElement> = createFactory(NullableKind.USELESS)
        val removeForLateInitProperty: QuickFixesPsiBasedFactory<KtElement> = createFactory(NullableKind.PROPERTY)

        private fun createFactory(typeOfError: NullableKind): QuickFixesPsiBasedFactory<KtElement> {
            return quickFixesPsiBasedFactory { e ->
                when (typeOfError) {
                    NullableKind.REDUNDANT, NullableKind.SUPERTYPE, NullableKind.USELESS -> {
                        val nullType: KtNullableType? = when (e) {
                            is KtTypeReference -> e.typeElement as? KtNullableType
                            else -> e.getNonStrictParentOfType()
                        }
                        if (nullType?.innerType == null) return@quickFixesPsiBasedFactory emptyList()
                        listOf(
                            RemoveNullableFix(nullType, typeOfError).asIntention()
                        )
                    }
                    NullableKind.PROPERTY -> {
                        val property = e as? KtProperty ?: return@quickFixesPsiBasedFactory emptyList()
                        val typeReference = property.typeReference ?: return@quickFixesPsiBasedFactory emptyList()
                        val typeElement = typeReference.typeElement as? KtNullableType ?: return@quickFixesPsiBasedFactory emptyList()
                        if (typeElement.innerType == null) return@quickFixesPsiBasedFactory emptyList()
                        listOf(
                            RemoveNullableFix(typeElement, NullableKind.PROPERTY).asIntention()
                        )
                    }
                }
            }
        }
    }
}
