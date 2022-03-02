/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.fixes.HLApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.fir.api.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.fir.api.fixes.withInput
import org.jetbrains.kotlin.idea.util.isRedundantSetter
import org.jetbrains.kotlin.idea.util.removeRedundantSetter
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType

object ChangeVisibilityFixFactories {

    class Input(val elementName: String) : HLApplicatorInput

    private fun getApplicator(visibilityModifier: KtModifierKeywordToken, forceUsingExplicitModifier: Boolean) =
        applicator<KtModifierListOwner, Input> {
            if (forceUsingExplicitModifier) {
                familyName { KotlinBundle.message("make.0.explicitly", visibilityModifier) }
                actionName { psi, input -> KotlinBundle.message("make.0.1.explicitly", input.elementName, visibilityModifier) }
            } else {
                familyName { KotlinBundle.message("make.0", visibilityModifier) }
                actionName { psi, input -> KotlinBundle.message("make.0.1", input.elementName, visibilityModifier) }
            }
            applyTo { psi, input ->
                // TODO: also add logic to change visibility of expect/actual declarations.
                if (visibilityModifier == KtTokens.PUBLIC_KEYWORD && !forceUsingExplicitModifier) {
                    psi.visibilityModifierType()?.let { psi.removeModifier(it) }
                } else {
                    psi.addModifier(visibilityModifier)
                }

                val propertyAccessor = psi as? KtPropertyAccessor
                if (propertyAccessor?.isRedundantSetter() == true) {
                    removeRedundantSetter(propertyAccessor)
                }
            }
        }

    val makePublicExplicitApplicator = getApplicator(KtTokens.PUBLIC_KEYWORD, true)

    val noExplicitVisibilityInApiMode =
        diagnosticFixFactory(KtFirDiagnostic.NoExplicitVisibilityInApiMode::class, makePublicExplicitApplicator) { diagnostic ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }
    val noExplicitVisibilityInApiModeWarning =
        diagnosticFixFactory(KtFirDiagnostic.NoExplicitVisibilityInApiModeWarning::class, makePublicExplicitApplicator) { diagnostic ->
            createFixForNoExplicitVisibilityInApiMode(diagnostic.psi)
        }

    private fun createFixForNoExplicitVisibilityInApiMode(declaration: KtDeclaration): List<HLApplicatorTargetWithInput<KtDeclaration, Input>> {
        val name = when (declaration) {
            is KtConstructor<*> -> SpecialNames.INIT.asString()
            is KtNamedDeclaration -> declaration.name ?: return emptyList()
            else -> return emptyList()
        }

        return listOf(declaration withInput Input(name))
    }
}