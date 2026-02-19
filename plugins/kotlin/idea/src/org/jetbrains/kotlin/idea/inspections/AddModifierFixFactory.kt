// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal sealed class AddModifierFixFactory(val token: KtModifierKeywordToken) : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val functionDescriptor = (diagnostic as? DiagnosticWithParameters2<*, *, *>)?.a as? FunctionDescriptor ?: return null
        val target = DescriptorToSourceUtilsIde.getAnyDeclaration(diagnostic.psiFile.project, functionDescriptor)
                as? KtModifierListOwner ?: return null
        if (target.canRefactorElement()) {
            return object : AddModifierFix(target, token), CleanupFix.ModCommand {}.asIntention()
        }
        return null
    }
}

internal object AddOperatorModifierFixFactory : AddModifierFixFactory(KtTokens.OPERATOR_KEYWORD)
internal object AddInfixModifierFixFactory : AddModifierFixFactory(KtTokens.INFIX_KEYWORD)