// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtConstructorDelegationReferenceExpression
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets

class KtConstructorDelegationReferenceDescriptorsImpl(
    expression: KtConstructorDelegationReferenceExpression
) : KtConstructorDelegationReference(expression), KtDescriptorsBasedReference {

    override fun getTargetDescriptors(context: BindingContext) = expression.getReferenceTargets(context)

    override fun isReferenceToImportAlias(alias: KtImportAlias): Boolean {
        return super<KtDescriptorsBasedReference>.isReferenceToImportAlias(alias)
    }
}
