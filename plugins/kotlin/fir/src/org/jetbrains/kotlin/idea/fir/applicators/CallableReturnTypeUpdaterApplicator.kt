// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.applicators

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtTypeRendererOptions
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

object CallableReturnTypeUpdaterApplicator {
    val applicator = applicator<KtCallableDeclaration, Type> {
        familyAndActionName(KotlinBundle.lazyMessage("fix.change.return.type.family"))

        applyTo { declaration, type, project ->
            val newTypeRef = if (!declaration.isProcedure(type)) {
                // TODO use longTypeRepresentation and then shorten
                KtPsiFactory(project).createType(type.shortTypeRepresentation)
            } else null
            runWriteAction {
                declaration.typeReference = newTypeRef
            }
        }
    }

    private fun KtCallableDeclaration.isProcedure(type: Type) =
        type.isUnit && this is KtFunction && hasBlockBody()

    class Type(
        val isUnit: Boolean,
        val longTypeRepresentation: String,
        val shortTypeRepresentation: String
    ) : HLApplicatorInput {
        override fun isValidFor(psi: PsiElement): Boolean = true

        companion object {
            fun KtAnalysisSession.createByKtType(ktType: KtType): Type = Type(
                isUnit = ktType.isUnit,
                longTypeRepresentation = ktType.render(KtTypeRendererOptions.DEFAULT),
                shortTypeRepresentation = ktType.render(KtTypeRendererOptions.SHORT_NAMES),
            )

            val UNIT = Type(isUnit = true, longTypeRepresentation = "kotlin.Unit", shortTypeRepresentation = "Unit")
        }
    }
}
