// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken

import org.jetbrains.kotlin.psi.KtModifierListOwner

object ModifierApplicators {
    fun removeModifierApplicator(modifier: TokenSet, familyName: () -> String) = applicator<KtModifierListOwner, Modifier> {
        familyName(familyName)
        actionName { _, (modifier) -> KotlinBundle.message("remove.0.modifier", modifier.value) }

        isApplicableByPsi { modifierOwner ->
            modifierOwner.modifierList?.getModifier(modifier) != null
        }

        applyTo { modifierOwner, (modifier) ->
            runWriteAction {
                modifierOwner.removeModifier(modifier)
            }
        }
    }

    class Modifier(val modifier: KtModifierKeywordToken) : KotlinApplicatorInput {
        override fun isValidFor(psi: PsiElement): Boolean {
            if (psi !is KtModifierListOwner) return false
            return psi.hasModifier(modifier)
        }

        operator fun component1() = modifier
    }
}