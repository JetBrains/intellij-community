// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionName
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration

typealias DeclarationPointer = SmartPsiElementPointer<KtCallableDeclaration>

object MakeOverriddenMemberOpenFixUtils {

    fun invoke(overriddenNonOverridableMembers: List<KtCallableDeclaration>) {
        for (overriddenMember in overriddenNonOverridableMembers) {
            overriddenMember.addModifier(KtTokens.OPEN_KEYWORD) // as a side effect, this may remove an incompatible modifier such as 'final'
            if (overriddenMember.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                // 'open' modifier is redundant on 'override' members and can be omitted
                overriddenMember.removeModifier(KtTokens.OPEN_KEYWORD)
            }
        }
    }

    @IntentionName
    fun getActionName(
        element: KtDeclaration,
        containingDeclarationNames: List<String>,
    ): String {
        if (containingDeclarationNames.size == 1) {
            val name = containingDeclarationNames[0] + "." + element.name
            return KotlinBundle.message("make.0", "$name ${KtTokens.OPEN_KEYWORD}")
        }
        val sortedDeclarationNames = containingDeclarationNames.sorted()
        val declarations =
            sortedDeclarationNames.subList(0, sortedDeclarationNames.size - 1).joinToString(", ") + " and " + sortedDeclarationNames.last()
        return KotlinBundle.message("make.0.in.1.open", element.name.toString(), declarations)
    }
}