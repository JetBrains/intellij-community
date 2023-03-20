// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.intentions.SpecifyExplicitLambdaSignatureIntention
import org.jetbrains.kotlin.idea.quickfix.SpecifyTypeExplicitlyFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.typeUtil.isNothing

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class FunctionWithLambdaExpressionBodyInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            check(function)
        }

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
            if (accessor.isSetter) return
            if (accessor.returnTypeReference != null) return
            check(accessor)
        }

        private fun check(element: KtDeclarationWithBody) {
            val callableDeclaration = element.getNonStrictParentOfType<KtCallableDeclaration>() ?: return
            if (callableDeclaration.typeReference != null) return
            val lambda = element.bodyExpression as? KtLambdaExpression ?: return
            val functionLiteral = lambda.functionLiteral
            if (functionLiteral.arrow != null || functionLiteral.valueParameterList != null) return
            val lambdaBody = functionLiteral.bodyBlockExpression ?: return

            val used = ReferencesSearch.search(callableDeclaration).any()
            val fixes = listOfNotNull(
                IntentionWrapper(SpecifyTypeExplicitlyFix()),
                IntentionWrapper(AddArrowIntention()),
                if (!used &&
                    lambdaBody.statements.size == 1 &&
                    lambdaBody.allChildren.none { it is PsiComment }
                )
                    RemoveBracesFix()
                else
                    null,
                if (!used) WrapRunFix() else null
            )
            holder.registerProblem(
                lambda,
                KotlinBundle.message("inspection.function.with.lambda.expression.body.display.name"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *fixes.toTypedArray()
            )
        }
    }

    private class AddArrowIntention : SpecifyExplicitLambdaSignatureIntention() {
        override fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean = false
    }

    private class RemoveBracesFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.braces.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val lambda = descriptor.psiElement as? KtLambdaExpression ?: return
            val singleStatement = lambda.functionLiteral.bodyExpression?.statements?.singleOrNull() ?: return
            val replaced = lambda.replaced(singleStatement)
            replaced.setTypeIfNeed()
        }
    }

    private class WrapRunFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("wrap.run.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val lambda = descriptor.psiElement as? KtLambdaExpression ?: return
            val body = lambda.functionLiteral.bodyExpression ?: return
            val replaced = lambda.replaced(KtPsiFactory(project).createExpressionByPattern("run { $0 }", body.allChildren))
            replaced.setTypeIfNeed()
        }
    }
}

private fun KtExpression.setTypeIfNeed() {
    val declaration = getStrictParentOfType<KtCallableDeclaration>() ?: return
    val type = (declaration.resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType
    if (type?.isNothing() == true) {
        declaration.setType(type)
    }
}
