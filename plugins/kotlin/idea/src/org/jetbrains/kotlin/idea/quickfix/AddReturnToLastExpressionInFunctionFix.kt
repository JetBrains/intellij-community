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
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

class AddReturnToLastExpressionInFunctionFix(element: KtDeclarationWithBody) : KotlinQuickFixAction<KtDeclarationWithBody>(element) {
    private val available: Boolean

    init {
        val namedFunction = element as? KtNamedFunction
        val block = namedFunction?.bodyBlockExpression
        val last = block?.statements?.lastOrNull()

        available = last?.analyze(BodyResolveMode.PARTIAL)?.let { context ->
            last.getType(context)?.takeIf { !it.isError }
        }?.let { lastType ->
            val expectedType = namedFunction.resolveToDescriptorIfAny()?.returnType?.takeIf { !it.isError } ?: return@let false
            lastType.isSubtypeOf(expectedType)
        } ?: false
    }

    override fun getText() = KotlinBundle.message("fix.add.return.last.expression")
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean =
        (element is KtNamedFunction) && available

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element as? KtNamedFunction ?: return
        val last = element.bodyBlockExpression?.statements?.lastOrNull() ?: return
        last.replace(KtPsiFactory(project).createExpression("return ${last.text}"))
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val casted = Errors.NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY.cast(diagnostic)
            return AddReturnToLastExpressionInFunctionFix(casted.psiElement).takeIf(AddReturnToLastExpressionInFunctionFix::available)
        }
    }
}
