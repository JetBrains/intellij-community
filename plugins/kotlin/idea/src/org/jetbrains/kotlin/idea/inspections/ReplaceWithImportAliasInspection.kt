// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceWithImportAliasInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = simpleNameExpressionVisitor (fun(expression) {
        if (expression !is KtNameReferenceExpression || expression.getIdentifier() == null || expression.isInImportDirective()) return
        val qualifiedElement = expression.getQualifiedElement()
        if (qualifiedElement !is KtDotQualifiedExpression && qualifiedElement !is KtUserType) return
        val aliasName = expression.aliasNameIdentifier()?.text ?: return
        holder.registerProblem(
            expression,
            KotlinBundle.message("replace.with.import.alias"),
            ReplaceWithImportAliasFix(aliasName)
        )
    })

    private class ReplaceWithImportAliasFix(private val aliasName: String): LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", aliasName)
        override fun getFamilyName() = name
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtNameReferenceExpression ?: return
            val aliasNameIdentifier = expression.aliasNameIdentifier() ?: return

            expression.getIdentifier()?.replace(aliasNameIdentifier.copy())
            expression.getQualifiedElement().replace(expression.parent.safeAs<KtCallExpression>() ?: expression)
        }
    }
}

private fun KtNameReferenceExpression.aliasNameIdentifier(): PsiElement? {
    val fqName = resolveMainReferenceToDescriptors().firstOrNull()?.importableFqName ?: return null
    return containingKtFile.findAliasByFqName(fqName)?.nameIdentifier
}
