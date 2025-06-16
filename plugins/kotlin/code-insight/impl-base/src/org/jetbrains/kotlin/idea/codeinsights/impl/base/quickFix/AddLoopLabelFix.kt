// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.psi.AddLoopLabelUtil.getExistingLabelName
import org.jetbrains.kotlin.idea.base.psi.AddLoopLabelUtil.getUniqueLabelName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

// This quick fix is not topical for any language version > 1.3, see https://youtrack.jetbrains.com/issue/KT-33413
class AddLoopLabelFix(
    loop: KtLoopExpression,
    jumpExpression: KtExpressionWithLabel
) : KotlinQuickFixAction<KtLoopExpression>(loop), LocalQuickFix {
    private val jumpExpressionPointer: SmartPsiElementPointer<KtExpressionWithLabel> = SmartPointerManager.createPointer(jumpExpression)
    private val existingLabelName = getExistingLabelName(loop)

    @Nls
    private val description: String = run {
        when {
            existingLabelName != null -> {
                val labelName = "@$existingLabelName"
                KotlinBundle.message("fix.add.loop.label.text", labelName, jumpExpression.text)
            }
            else -> {
                KotlinBundle.message("fix.add.loop.label.text.generic")
            }
        }
    }

    override fun getText(): @Nls String = description
    override fun getFamilyName(): @Nls String = text

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        applyFix()
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        applyFix()
    }

    private fun applyFix() {
        val loopExpression = element ?: return
        val jumpExpression = jumpExpressionPointer.element ?: return
        addLoopLabel(loopExpression, jumpExpression)
    }

    private fun addLoopLabel(loopExpression: KtLoopExpression, jumpExpression: KtExpressionWithLabel): KtExpression {
        val labelName = getExistingLabelName(loopExpression) ?: getUniqueLabelName(loopExpression)

        val ktPsiFactory = KtPsiFactory(loopExpression.project)
        jumpExpression.replace(ktPsiFactory.createExpression(jumpExpression.text + "@" + labelName))

        return if (getExistingLabelName(loopExpression) == null) {
            loopExpression.replaced(
                ktPsiFactory.createExpressionByPattern("$0@ $1", labelName, loopExpression, reformat = false)
            )
        } else {
            loopExpression
        }
    }
}
