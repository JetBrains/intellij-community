// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*

// In K2, the counterpart is org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.UnusedVariableInspection,
// see https://youtrack.jetbrains.com/issue/KTIJ-29532/K2-IDE-Port-RenameToUnderscoreFix#focus=Change-27-10072644.0-0
class RenameToUnderscoreFix(element: KtCallableDeclaration) : KotlinQuickFixAction<KtCallableDeclaration>(element) {
    override fun getText() = KotlinBundle.message("rename.to.underscore")
    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.nameIdentifier?.replace(KtPsiFactory(project).createIdentifier("_"))
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val declaration: KtCallableDeclaration? = when (diagnostic.factory) {
                Errors.UNUSED_ANONYMOUS_PARAMETER -> {
                    val parameter = diagnostic.psiElement as? KtParameter
                    val owner = parameter?.parent?.parent

                    if (owner is KtFunctionLiteral || (owner is KtNamedFunction && owner.name == null))
                        parameter
                    else
                        null
                }
                Errors.UNUSED_VARIABLE, Errors.UNUSED_DESTRUCTURED_PARAMETER_ENTRY ->
                    diagnostic.psiElement as? KtDestructuringDeclarationEntry
                else -> null
            }

            if (declaration?.nameIdentifier == null || !declaration.languageVersionSettings
                    .supportsFeature(LanguageFeature.SingleUnderscoreForParameterName)
            ) {
                return null
            }

            return RenameToUnderscoreFix(declaration)
        }
    }
}
