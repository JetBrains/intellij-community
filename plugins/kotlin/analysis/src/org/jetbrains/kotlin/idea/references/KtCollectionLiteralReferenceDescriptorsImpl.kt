// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.resolve.BindingContext

class KtCollectionLiteralReferenceDescriptorsImpl(
    expression: KtCollectionLiteralExpression
) : KtCollectionLiteralReference(expression), KtDescriptorsBasedReference {
    override fun getTargetDescriptors(context: BindingContext): Collection<DeclarationDescriptor> {
        val resolvedCall = context[BindingContext.COLLECTION_LITERAL_CALL, element]
        return listOfNotNull(resolvedCall?.resultingDescriptor)
    }

    override fun isReferenceToImportAlias(alias: KtImportAlias): Boolean {
        return super<KtDescriptorsBasedReference>.isReferenceToImportAlias(alias)
    }
}
