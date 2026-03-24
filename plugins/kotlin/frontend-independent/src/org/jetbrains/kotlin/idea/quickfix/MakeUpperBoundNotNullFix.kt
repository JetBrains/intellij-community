// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.renderer.render

class MakeUpperBoundNotNullFix(typeParameter: KtTypeParameter,
                               private val kind: Kind) : PsiUpdateModCommandAction<KtTypeParameter>(typeParameter) {

    sealed class Kind {
        abstract val renderedUpperBound: String

        @IntentionName
        abstract fun getText(parameter: KtTypeParameter): String

        /**
         * Add `Any` as an upper bound
         */
        object AddAnyAsUpperBound : Kind() {
            override val renderedUpperBound: String = StandardNames.FqNames.any.render()

            override fun getText(parameter: KtTypeParameter): String = KotlinBundle.message(
                "fix.make.upperbound.not.nullable.any.text",
                parameter.name ?: ""
            )
        }

        /**
         * Replace an existing upper bound with another upper bound
         */
        class ReplaceExistingUpperBound(override val renderedUpperBound: String) : Kind() {
            override fun getText(parameter: KtTypeParameter): String = KotlinBundle.message(
                "fix.make.upperbound.not.nullable.remove.nullability.text",
                parameter.name ?: "",
                renderedUpperBound
            )
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("fix.make.upperbound.not.nullable.family")

    override fun getPresentation(
        context: ActionContext,
        element: KtTypeParameter,
    ): Presentation? {
        if (element.name == null) return null
        val applicable = when (kind) {
            Kind.AddAnyAsUpperBound -> element.extendsBound == null
            is Kind.ReplaceExistingUpperBound -> element.extendsBound != null
        }
        if (!applicable) return null
        return Presentation.of(kind.getText(element))
    }

    override fun invoke(
        context: ActionContext,
        element: KtTypeParameter,
        updater: ModPsiUpdater
    ) {
        val typeReference = KtPsiFactory(element.project).createType(kind.renderedUpperBound)
        val insertedTypeReference = element.setExtendsBound(typeReference) ?: return
        ShortenReferencesFacility.getInstance().shorten(insertedTypeReference)
    }
}
