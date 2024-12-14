// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.AddLoopLabelFix
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertIfToWhen
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.getSubjectToIntroduce
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.introduceSubjectIfPossible
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

internal class IfToWhenIntention : KotlinApplicableModCommandAction<KtIfExpression, Unit>(KtIfExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("replace.if.with.when")

    context(KaSession)
    override fun prepareContext(element: KtIfExpression) {}

    override fun invoke(
        actionContext: ActionContext,
        element: KtIfExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        convertIfToWhen(element, updater)
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> =
        ApplicabilityRanges.ifKeyword(element)

    override fun isApplicableByPsi(element: KtIfExpression): Boolean = element.then != null
}
