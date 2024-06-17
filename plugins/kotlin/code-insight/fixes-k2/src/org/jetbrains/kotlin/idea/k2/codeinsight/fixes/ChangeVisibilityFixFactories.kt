// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantSetter
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

object ChangeVisibilityFixFactories {

    private data class ElementContext(
        val elementName: String,
    )

    private class ChangeVisibilityModCommandAction(
        element: KtDeclaration,
        elementContext: ElementContext,
        private val forceUsingExplicitModifier: Boolean,
        private val visibilityModifier: KtModifierKeywordToken,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtDeclaration, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String = KotlinBundle.message(
            if (forceUsingExplicitModifier) "make.0.explicitly" else "make.0",
            visibilityModifier,
        )

        override fun getActionName(
            actionContext: ActionContext,
            element: KtDeclaration,
            elementContext: ElementContext,
        ): String = KotlinBundle.message(
            if (forceUsingExplicitModifier) "make.0.1.explicitly" else "make.0.1",
            elementContext.elementName,
            visibilityModifier,
        )

        override fun invoke(
            actionContext: ActionContext,
            element: KtDeclaration,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            // TODO: also add logic to change visibility of expect/actual declarations.
            if (visibilityModifier == KtTokens.PUBLIC_KEYWORD
                && !forceUsingExplicitModifier
            ) {
                element.visibilityModifierType()?.let { element.removeModifier(it) }
            } else {
                element.addModifier(visibilityModifier)
            }

            val propertyAccessor = element as? KtPropertyAccessor
            if (propertyAccessor?.isRedundantSetter() == true) {
                removeRedundantSetter(propertyAccessor)
            }
        }
    }

    val noExplicitVisibilityInApiMode =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoExplicitVisibilityInApiMode ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }

    val noExplicitVisibilityInApiModeWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NoExplicitVisibilityInApiModeWarning ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }

    context(KaSession)
    private fun createFixForNoExplicitVisibilityInApiMode(
        element: KtDeclaration,
    ): List<ChangeVisibilityModCommandAction> {
        val elementName = when (element) {
            is KtConstructor<*> -> SpecialNames.INIT.asString()
            is KtNamedDeclaration -> element.name
            else -> null
        } ?: return emptyList()

        return listOf(
            ChangeVisibilityModCommandAction(
                element = element,
                elementContext = ElementContext(elementName),
                forceUsingExplicitModifier = true,
                visibilityModifier = KtTokens.PUBLIC_KEYWORD,
            )
        )
    }
}