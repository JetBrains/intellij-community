/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticModCommandFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantSetter
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

object ChangeVisibilityFixFactories {

    data class Input(
        val elementName: String,
    ) : KotlinApplicatorInput

    private abstract class ApplicatorImpl(
        protected val visibilityModifier: KtModifierKeywordToken,
    ) : KotlinApplicator.ModCommandBased<KtModifierListOwner, Input> {

        override fun applyTo(
            psi: KtModifierListOwner,
            input: Input,
            context: ActionContext,
            updater: ModPsiUpdater,
        ) {
            val propertyAccessor = psi as? KtPropertyAccessor
            if (propertyAccessor?.isRedundantSetter() == true) {
                removeRedundantSetter(propertyAccessor)
            }
        }
    }

    private fun getForcedApplicator(modifier: KtModifierKeywordToken) = object : ApplicatorImpl(modifier) {

        override fun getFamilyName(): String = KotlinBundle.message("make.0.explicitly", visibilityModifier)

        override fun getActionName(
            psi: KtModifierListOwner,
            input: Input,
        ): String = KotlinBundle.message(
            "make.0.1.explicitly",
            input.elementName,
            visibilityModifier,
        )

        override fun applyTo(
            psi: KtModifierListOwner,
            input: Input,
            context: ActionContext,
            updater: ModPsiUpdater,
        ) {
            psi.addModifier(visibilityModifier)
            super.applyTo(psi, input, context, updater)
        }
    }

    private fun getApplicator(modifier: KtModifierKeywordToken) = object : ApplicatorImpl(modifier) {

        override fun getFamilyName(): String = KotlinBundle.message("make.0", visibilityModifier)

        override fun getActionName(
            psi: KtModifierListOwner,
            input: Input,
        ): String = KotlinBundle.message(
            "make.0.1",
            input.elementName,
            visibilityModifier,
        )

        override fun applyTo(
            psi: KtModifierListOwner,
            input: Input,
            context: ActionContext,
            updater: ModPsiUpdater,
        ) {
            // TODO: also add logic to change visibility of expect/actual declarations.
            when (visibilityModifier) {
                KtTokens.PUBLIC_KEYWORD -> psi.visibilityModifierType()?.let { psi.removeModifier(it) }
                else -> psi.addModifier(visibilityModifier)
            }

            super.applyTo(psi, input, context, updater)
        }
    }

    private val makePublicExplicitApplicator = getForcedApplicator(KtTokens.PUBLIC_KEYWORD)

    val noExplicitVisibilityInApiMode =
        diagnosticModCommandFixFactory(KtFirDiagnostic.NoExplicitVisibilityInApiMode::class, makePublicExplicitApplicator) { diagnostic ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }
    val noExplicitVisibilityInApiModeWarning =
        diagnosticModCommandFixFactory(
            KtFirDiagnostic.NoExplicitVisibilityInApiModeWarning::class,
            makePublicExplicitApplicator
        ) { diagnostic ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }

    private fun createFixForNoExplicitVisibilityInApiMode(declaration: KtDeclaration): List<KotlinApplicatorTargetWithInput<KtDeclaration, Input>> {
        val name = when (declaration) {
            is KtConstructor<*> -> SpecialNames.INIT.asString()
            is KtNamedDeclaration -> declaration.name ?: return emptyList()
            else -> return emptyList()
        }

        return listOf(declaration withInput Input(name))
    }
}