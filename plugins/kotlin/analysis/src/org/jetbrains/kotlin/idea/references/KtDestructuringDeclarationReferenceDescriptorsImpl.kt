// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.resolve.BindingContext

class KtDestructuringDeclarationReferenceDescriptorsImpl(
    element: KtDestructuringDeclarationEntry
) : KtDestructuringDeclarationReference(element), KtDescriptorsBasedReference {

    override fun getTargetDescriptors(context: BindingContext): Collection<DeclarationDescriptor> {
        return listOfNotNull(
            context[BindingContext.VARIABLE, element],
            context[BindingContext.COMPONENT_RESOLVED_CALL, element]?.candidateDescriptor
        )
    }

    override fun resolve() = multiResolve(false).asSequence()
        .map { it.element }
        .first { it is KtDestructuringDeclarationEntry }

    override fun getRangeInElement() = TextRange(0, element.textLength)

    override fun isReferenceToImportAlias(alias: KtImportAlias): Boolean {
        return super<KtDescriptorsBasedReference>.isReferenceToImportAlias(alias)
    }

    override fun canRename(): Boolean {
        val bindingContext = expression.analyze() //TODO: should it use full body resolve?
        return resolveToDescriptors(bindingContext).all {
            it is CallableMemberDescriptor && it.kind == CallableMemberDescriptor.Kind.SYNTHESIZED
        }
    }
}
