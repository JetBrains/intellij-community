// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.refactoring.project
import org.jetbrains.kotlin.idea.references.getCalleeByLambdaArgument
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getLabeledParent
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class RenameByLabeledReferenceHandler : AbstractReferenceSubstitutionRenameHandler(KotlinVariableInplaceRenameHandler()) {
    override fun getElementToRename(dataContext: DataContext): PsiElement? {
        val refExpr = getReferenceExpression(dataContext) as? KtLabelReferenceExpression ?: return null
        val context = refExpr.analyze(BodyResolveMode.PARTIAL)
        val labelTarget = context[BindingContext.LABEL_TARGET, refExpr] as? KtExpression ?: return null
        val labeledParent = labelTarget.getLabeledParent(refExpr.getReferencedName())
        if (labelTarget !is KtFunction || labeledParent != null) return labeledParent
        val calleeExpression = labelTarget.getCalleeByLambdaArgument() ?: return null
        val descriptor = context[BindingContext.REFERENCE_TARGET, calleeExpression] as? FunctionDescriptor ?: return null
        return DescriptorToSourceUtilsIde.getAnyDeclaration(dataContext.project, descriptor)
    }
}
