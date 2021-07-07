// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhenCondition
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveWhenConditionFix(element: KtWhenCondition) : KotlinQuickFixAction<KtWhenCondition>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.condition")

    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.let { EditCommaSeparatedListHelper.removeItem(it) }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): RemoveWhenConditionFix? {
            return when (diagnostic.factory) {
                Errors.SENSELESS_NULL_IN_WHEN -> {
                    val whenCondition = diagnostic.psiElement.getStrictParentOfType<KtWhenCondition>() ?: return null
                    val whenEntry = whenCondition.parent as? KtWhenEntry ?: return null
                    if (whenEntry.conditions.size >= 2) RemoveWhenConditionFix(whenCondition) else null
                }
                else -> null
            }
        }
    }
}
