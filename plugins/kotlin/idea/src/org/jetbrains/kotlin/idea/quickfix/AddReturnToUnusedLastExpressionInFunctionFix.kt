// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class AddReturnToUnusedLastExpressionInFunctionFix(element: KtElement) : KotlinQuickFixAction<KtElement>(element) {

    private val available: Boolean

    init {
        val expression = element as? KtExpression
        available = expression?.analyze(BodyResolveMode.PARTIAL)?.let { context ->
            if (expression.isLastStatementInFunctionBody()) {
                expression.getType(context)?.takeIf { !it.isError }
            } else null
        }?.let { expressionType ->
            val function = expression.parent?.parent as? KtNamedFunction
            val functionReturnType = function?.resolveToDescriptorIfAny()?.returnType?.takeIf { !it.isError } ?: return@let false
            expressionType.isSubtypeOf(functionReturnType)
        } ?: false

    }

    override fun getText() = KotlinBundle.message("fix.add.return.before.expression")
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean =
        element != null && available

    private fun KtExpression.isLastStatementInFunctionBody(): Boolean {
        val body = this.parent as? KtBlockExpression ?: return false
        val last = body.statements.lastOrNull() ?: return false
        return last === this
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        element.replace(KtPsiFactory(project).createExpression("return ${element.text}"))
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val casted = Errors.UNUSED_EXPRESSION.cast(diagnostic)
            return AddReturnToUnusedLastExpressionInFunctionFix(casted.psiElement).takeIf(AddReturnToUnusedLastExpressionInFunctionFix::available)
        }
    }
}
