// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.descriptors.accessors
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext

class KtPropertyDelegationMethodsReferenceDescriptorsImpl(
    element: KtPropertyDelegate
) : KtPropertyDelegationMethodsReference(element), KtDescriptorsBasedReference {

    override fun getTargetDescriptors(context: BindingContext): Collection<DeclarationDescriptor> {
        val property = expression.getStrictParentOfType<KtProperty>() ?: return emptyList()
        val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, property] as? VariableDescriptorWithAccessors
            ?: return emptyList()
        return descriptor.accessors.mapNotNull { accessor ->
            context.get(BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, accessor)?.candidateDescriptor
        } + listOfNotNull(context.get(BindingContext.PROVIDE_DELEGATE_RESOLVED_CALL, descriptor)?.candidateDescriptor)
    }

    override fun isReferenceToImportAlias(alias: KtImportAlias): Boolean {
        return super<KtDescriptorsBasedReference>.isReferenceToImportAlias(alias)
    }
}
