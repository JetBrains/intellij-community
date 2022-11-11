// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.InspectionGadgetsBundle
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.RenameToUnderscoreFix
import org.jetbrains.kotlin.idea.util.hasComments
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtVisitorVoid
import javax.swing.JComponent

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class KotlinCatchMayIgnoreExceptionInspection : AbstractKotlinInspection() {
    var ignoreCatchBlocksWithComments: Boolean = true

    override fun createOptionsPanel(): JComponent? {
        return InspectionOptionsPanel.singleCheckBox(this,
            InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.comments"),
        "ignoreCatchBlocksWithComments")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitCatchSection(catchClause: KtCatchClause) {
                val catchParameter = catchClause.catchParameter ?: return
                val name = catchParameter.name ?: return
                if (PsiUtil.isIgnoredName(name)) return
                if (name == "_") return
                val catchBody = catchClause.catchBody ?: return
                if (catchBody is KtBlockExpression && catchBody.statements.isEmpty()) {
                    if (ignoreCatchBlocksWithComments && catchBody.hasComments()) return
                    holder.registerProblem((catchClause as PsiElement).firstChild!!,
                        KotlinBundle.message("inspection.message.empty.catch.block"),
                        RenameToUnderscoreLocalQuickFix(name))
                }
            }
        }
    }

    class RenameToUnderscoreLocalQuickFix(val parameterName: String): LocalQuickFix {
        override fun getName(): String = CommonQuickFixBundle.message("fix.rename.x.to.y", parameterName, "_")

        override fun getFamilyName(): String = KotlinBundle.message("rename.to.underscore")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val catchClause = PsiTreeUtil.getParentOfType(descriptor.startElement, KtCatchClause::class.java) ?: return
            val parameter = catchClause.catchParameter ?: return
            RenameToUnderscoreFix(parameter).invoke(project, null, parameter.containingFile)
        }
    }
}