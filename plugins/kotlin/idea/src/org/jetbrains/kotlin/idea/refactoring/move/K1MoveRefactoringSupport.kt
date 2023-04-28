// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS

internal class K1MoveRefactoringSupport : KotlinMoveRefactoringSupport {
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {
        return KotlinFindUsagesHandlerFactory(target.project).createFindUsagesHandler(target, false)
            .findReferencesToHighlight(target, searchScope)
    }

    override fun isExtensionRef(expr: KtSimpleNameExpression): Boolean {
        val resolvedCall = expr.getResolvedCall(expr.analyze(BodyResolveMode.PARTIAL)) ?: return false
        if (resolvedCall is VariableAsFunctionResolvedCall) {
            return resolvedCall.variableCall.candidateDescriptor.isExtension || resolvedCall.functionCall.candidateDescriptor.isExtension
        }
        return resolvedCall.candidateDescriptor.isExtension
    }

    override fun isQualifiable(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
        val receiverExpression = callableReferenceExpression.receiverExpression
        val lhs = callableReferenceExpression.analyze(BodyResolveMode.PARTIAL)[BindingContext.DOUBLE_COLON_LHS, receiverExpression]
        return lhs is DoubleColonLHS.Type
    }
}