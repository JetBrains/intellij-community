// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreatePropertyDelegateAccessorsActionFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.util.OperatorNameConventions

class ReassignmentActionFactory(val factory: DiagnosticFactory1<*, DeclarationDescriptor>) : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val propertyDescriptor = factory.cast(diagnostic).a
        val declaration =
            DescriptorToSourceUtils.descriptorToDeclaration(propertyDescriptor) as? KtValVarKeywordOwner ?: return null
        return ChangeVariableMutabilityFix(declaration, true)
    }
}

object DelegatedPropertyValFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val element = Errors.DELEGATE_SPECIAL_FUNCTION_MISSING.cast(diagnostic).psiElement
        val property = element.getStrictParentOfType<KtProperty>() ?: return null
        val info = CreatePropertyDelegateAccessorsActionFactory.extractFixData(property, diagnostic).singleOrNull() ?: return null
        if (info.name != OperatorNameConventions.SET_VALUE.asString()) return null
        return ChangeVariableMutabilityFix(property, makeVar = false, actionText = KotlinBundle.message("change.to.val"))
    }
}