// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.types.Variance

internal object ReceiverShadowedByContextParameterFactory {
    @OptIn(KaExperimentalApi::class)
    val addReceiverFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReceiverShadowedByContextParameter ->
        val expression = diagnostic.psi
            .parents(withSelf = true)
            .takeWhile { it is KtNameReferenceExpression || it is KtCallExpression || it is KtQualifiedExpression }
            .lastOrNull() as? KtExpression
            ?: return@ModCommandBased emptyList()

        buildList {
            add(AddExplicitThisFix(expression, diagnostic.isDispatchOfMemberExtension))
            for (symbol in diagnostic.contextParameterSymbols) {
                if (symbol is KaContextParameterSymbol) {
                    val nameOrContextOfCall = if (symbol.name.isSpecial) {
                        val approximatedType = symbol.returnType.approximateToDenotableSupertypeOrSelf(allowLocalDenotableTypes = true)
                        val renderedType = approximatedType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
                        "contextOf<$renderedType>()"
                    } else {
                        symbol.name.identifier
                    }
                    add(AddContextParameterReceiverFix(expression, diagnostic.isDispatchOfMemberExtension, nameOrContextOfCall))
                }
            }
        }
    }
}

private class AddExplicitThisFix(
    expression: KtExpression,
    private val isDispatchOfMemberExtension: Boolean
) : PsiUpdateModCommandAction<KtExpression>(expression) {
    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        val factory = KtPsiFactory(context.project)
        element.replace(
            factory.createExpression(
                if (isDispatchOfMemberExtension) {
                    "with(this) { ${element.text} }"
                } else {
                    "this.${element.text}"
                }
            )
        )
    }

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation {
        return Presentation.of(
            KotlinBundle.message(
                if (isDispatchOfMemberExtension) {
                    "fix.receiver.shadowed.by.context.add.explicit.receiver.surround.with"
                } else {
                    "fix.receiver.shadowed.by.context.add.explicit.receiver.this"
                }
            )
        )
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.receiver.shadowed.by.context.add.explicit.receiver.family")
}


private class AddContextParameterReceiverFix(
    expression: KtExpression,
    private val isDispatchOfMemberExtension: Boolean,
    private val nameOrContextOfCall: String
) : PsiUpdateModCommandAction<KtExpression>(expression) {
    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        val factory = KtPsiFactory(context.project)
        element.replace(
            factory.createExpression(
                if (isDispatchOfMemberExtension) {
                    "with($nameOrContextOfCall) { ${element.text} }"
                } else {
                    "$nameOrContextOfCall.${element.text}"
                }
            )
        )
    }

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation {
        return Presentation.of(
            KotlinBundle.message(
                if (isDispatchOfMemberExtension) {
                    "fix.receiver.shadowed.by.context.add.explicit.receiver.surround.with.context"
                } else {
                    "fix.receiver.shadowed.by.context.add.explicit.receiver.context"
                },
                nameOrContextOfCall
            )
        )
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.receiver.shadowed.by.context.add.explicit.receiver.family")
}
