// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions.REM_TO_MOD_OPERATION_NAMES

class RenameModToRemFix(element: KtNamedFunction, val newName: Name) : KotlinQuickFixAction<KtNamedFunction>(element) {
    override fun getText(): String = KotlinBundle.message("rename.to.0", newName)

    override fun getFamilyName(): String = text

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        RenameProcessor(project, element ?: return, newName.asString(), false, false).run()
    }

    companion object Factory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            if (diagnostic.factory != Errors.DEPRECATED_BINARY_MOD && diagnostic.factory != Errors.FORBIDDEN_BINARY_MOD) return null

            val operatorMod = diagnostic.psiElement.getNonStrictParentOfType<KtNamedFunction>() ?: return null
            val newName = operatorMod.nameAsName?.let { REM_TO_MOD_OPERATION_NAMES.inverse()[it] } ?: return null
            return RenameModToRemFix(operatorMod, newName)
        }
    }
}