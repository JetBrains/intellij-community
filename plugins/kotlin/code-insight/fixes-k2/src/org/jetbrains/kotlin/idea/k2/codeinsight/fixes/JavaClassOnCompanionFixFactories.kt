// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

internal object JavaClassOnCompanionFixFactories {

    val factory = KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.JavaClassOnCompanion ->
        val element = diagnostic.psi as? KtDotQualifiedExpression ?: return@KotlinQuickFixFactory emptyList()
        val elementContext = (element.receiverExpression.mainReference?.resolve() as? KtObjectDeclaration)?.name
            ?: SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.identifier

        listOf(
            ReplaceWithCompanionClassJavaFix(element, elementContext),
            ReplaceWithClassJavaFix(element)
        )
    }
}

private class ReplaceWithCompanionClassJavaFix(
    element: KtDotQualifiedExpression,
    private val companionName: String,
) : PsiUpdateModCommandAction<KtDotQualifiedExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.0", "Companion::class.java")

    override fun getPresentation(context: ActionContext, element: KtDotQualifiedExpression): Presentation =
        Presentation.of(KotlinBundle.message("replace.with.0", "$companionName::class.java"))

    override fun invoke(
        actionContext: ActionContext,
        element: KtDotQualifiedExpression,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        element.replace(
            psiFactory.createExpressionByPattern("$0.$companionName::class.java", element.receiverExpression)
        )
    }
}

private class ReplaceWithClassJavaFix(
    element: KtDotQualifiedExpression,
) : PsiUpdateModCommandAction<KtDotQualifiedExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.0", "::class.java")

    override fun getPresentation(context: ActionContext, element: KtDotQualifiedExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun invoke(
        context: ActionContext,
        element: KtDotQualifiedExpression,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(context.project)
        element.replace(
            psiFactory.createExpressionByPattern("$0::class.java", element.receiverExpression)
        )
    }
}
