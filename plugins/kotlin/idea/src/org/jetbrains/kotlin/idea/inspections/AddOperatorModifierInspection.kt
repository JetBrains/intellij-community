// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.util.nameIdentifierTextRangeInThis
import org.jetbrains.kotlin.idea.util.withExpectedActuals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.util.OperatorChecks

class AddOperatorModifierInspection : AbstractApplicabilityBasedInspection<KtNamedFunction>(KtNamedFunction::class.java) {
    override fun inspectionHighlightRangeInElement(element: KtNamedFunction) = element.nameIdentifierTextRangeInThis()

    override fun inspectionText(element: KtNamedFunction) = KotlinBundle.message("function.should.have.operator.modifier")

    override val defaultFixText get() = KotlinBundle.message("add.operator.modifier")

    override fun isApplicable(element: KtNamedFunction): Boolean {
        if (element.nameIdentifier == null || element.hasModifier(KtTokens.OPERATOR_KEYWORD)) return false
        val functionDescriptor = element.resolveToDescriptorIfAny() ?: return false
        return !functionDescriptor.isOperator && OperatorChecks.check(functionDescriptor).isSuccess
    }

    override fun applyTo(element: KtNamedFunction, project: Project, editor: Editor?) {
        val declarations = element.withExpectedActuals()
        runWriteAction {
            for (declaration in declarations) {
                declaration.addModifier(KtTokens.OPERATOR_KEYWORD)
            }
        }
    }
}
