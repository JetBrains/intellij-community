// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.isInImportDirective
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceWithImportAliasInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = simpleNameExpressionVisitor(fun(expression) {
        if (expression !is KtNameReferenceExpression || expression.getIdentifier() == null || expression.isInImportDirective()) return
        val qualifiedElement = expression.getQualifiedElement()
        if (qualifiedElement !is KtDotQualifiedExpression && qualifiedElement !is KtUserType) return
        val aliasNameIdentifier = expression.aliasNameIdentifier() ?: return
        holder.registerProblem(
            expression,
            KotlinBundle.message("replace.with.import.alias"),
            ReplaceWithImportAliasFix(aliasNameIdentifier.createSmartPointer(), aliasNameIdentifier.text)
        )
    })

    private fun KtNameReferenceExpression.aliasNameIdentifier(): PsiElement? {
        val name = getIdentifier()?.text ?: return null
        val imports = containingKtFile.importDirectives.filter {
            !it.isAllUnder && it.alias != null && it.importedFqName?.shortName()?.asString() == name
        }.ifEmpty { return null }

        val fqName = resolveMainReferenceToDescriptors().firstOrNull()?.importableFqName ?: return null
        return imports.find { it.importedFqName == fqName }?.alias?.nameIdentifier
    }

    private class ReplaceWithImportAliasFix(
        private val aliasNameIdentifierPointer: SmartPsiElementPointer<PsiElement>,
        private val aliasName: String
    ) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", aliasName)
        override fun getFamilyName() = name
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtNameReferenceExpression ?: return
            val aliasNameIdentifier = aliasNameIdentifierPointer.element ?: return
            expression.getIdentifier()?.replace(aliasNameIdentifier.copy())
            expression.getQualifiedElement().let { element ->
                if (element is KtUserType) {
                    element.referenceExpression?.replace(expression)
                    element.deleteQualifier()
                } else {
                    element.replace(expression.parent.safeAs<KtCallExpression>() ?: expression)
                }
            }
        }
    }
}
