// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.endOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeIterated
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

internal class IterateExpressionIntention : KotlinApplicableModCommandAction<KtExpression, Unit>(KtExpression::class) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        val psiFactory = KtPsiFactory(element.project)
        val iteratorExpression = psiFactory.createExpressionByPattern("$0.iterator()", element)

        val suggestedName = analyze(iteratorExpression) {
            val iterableType = iteratorExpression.expressionType ?: return
            val nameValidator = KotlinDeclarationNameValidator(
                element,
                true,
                KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE,
            )
            /* After KTIJ-32329 `Support destructive declarations for .for/.iter postfixes, IterateExpressionIntention` is done:
            to align with K1 for Maps:
            Take two names from `KotlinNameSuggester().suggestTypeNames`, not only `first()`, then:
            `psiFactory.createDestructuringParameter(names.indices.joinToString(prefix = "(", postfix = ")") { "p$it" }))`
            See the K1 implementation around org/jetbrains/kotlin/idea/intentions/IterateExpressionIntention.kt:92
            */
            with(KotlinNameSuggester()) {
                suggestTypeNames(iterableType).map { typeName ->
                    KotlinNameSuggester.suggestNameByName(typeName) { nameValidator.validate(it) }
                }
            }.first()
        }

        var forExpression =
            psiFactory.createExpressionByPattern("for($0 in $1) {\nx\n}", suggestedName, element) as KtForExpression
        forExpression = element.replaced(forExpression)

        val modTemplateBuilder = updater.templateBuilder()
        forExpression.loopParameter?.let {
            modTemplateBuilder.field(it, TextExpression(suggestedName))
        }
        forExpression.body?.let { forExpressionBody ->
            if (forExpressionBody is KtBlockExpression) {
                // The function call below is the same hack as done in K1 implementation in
                // ~org/jetbrains/kotlin/idea/intentions/IterateExpressionIntention.kt:105
                modTemplateBuilder.field(
                    // We replace the placeholder `x` with "" to make a PSI-element to put the caret near it.
                    // It's impossible to reflect an empty element above in the pattern
                    forExpressionBody.statements.single(), // it's `x`
                    /* varName = */ "",
                    /* dependantVariableName = */ "",
                    /* alwaysStopAt = */ false // This is needed to not making this place editable when staying there with the caret
                )
            }
            modTemplateBuilder.finishAt(forExpressionBody.endOffset - 2)
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun prepareContext(element: KtExpression): Unit? {
        if (element.parent !is KtBlockExpression) return null

        return analyze(element) {
            val expressionType = element.expressionType as? KaClassType ?: return null
            if (!canBeIterated(expressionType)) return null
            Unit
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("iterate.over.collection")

    @OptIn(KaExperimentalApi::class)
    override fun getPresentation(
        context: ActionContext,
        element: KtExpression,
    ): Presentation? {
        val typePresentation = analyze(element) {
            val expressionType = element.expressionType ?: return null
            expressionType.render(
                renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
                position = Variance.IN_VARIANCE,
            )
        }
        return Presentation.of(KotlinBundle.message("iterate.over.0", typePresentation, context))
            .withPriority(PriorityAction.Priority.LOW)
    }
}