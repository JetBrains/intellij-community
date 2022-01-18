// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeIntention
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

open class RemovePsiElementSimpleFix(element: PsiElement, @Nls private val text: String) : KotlinQuickFixAction<PsiElement>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.element")

    override fun getText() = text

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.delete()
    }

    object RemoveImportFactory : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<PsiElement>? {
            val directive = diagnostic.psiElement.getNonStrictParentOfType<KtImportDirective>() ?: return null
            val refText = directive.importedReference?.let { KotlinBundle.message("for.0", it.text) } ?: ""
            return RemovePsiElementSimpleFix(directive, KotlinBundle.message("remove.conflicting.import.0", refText))
        }
    }

    object RemoveSpreadFactory : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<PsiElement>? {
            val element = diagnostic.psiElement
            if (element.node.elementType != KtTokens.MUL) return null
            return RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.star"))
        }
    }

    object RemoveTypeArgumentsFactory : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<PsiElement>? {
            val element = diagnostic.psiElement.getNonStrictParentOfType<KtTypeArgumentList>() ?: return null
            return RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.type.arguments"))
        }
    }

    object RemoveTypeParametersFactory : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<PsiElement>? {
            val element = diagnostic.psiElement.getNonStrictParentOfType<KtTypeParameterList>() ?: return null
            return RemovePsiElementSimpleFix(element, KotlinBundle.message("remove.type.parameters"))
        }
    }

    object RemoveVariableFactory : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<PsiElement>? {
            val element = diagnostic.psiElement
            if (element is KtDestructuringDeclarationEntry) return null
            val expression = element.getNonStrictParentOfType<KtProperty>() ?: return null
            if (!RemoveExplicitTypeIntention.redundantTypeSpecification(expression.typeReference, expression.initializer)) return null
            return RemoveVariableFix(expression)
        }
    }

    private class RemoveVariableFix(expression: KtProperty) :
        RemovePsiElementSimpleFix(expression, KotlinBundle.message("remove.variable.0", expression.name.toString())) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val expr = element as? KtProperty ?: return
            val initializer = expr.initializer
            if (initializer != null && initializer !is KtConstantExpression) {
                val commentSaver = CommentSaver(expr)
                val replaced = expr.replace(initializer)
                commentSaver.restore(replaced)
            } else {
                expr.delete()
            }
        }
    }
}
