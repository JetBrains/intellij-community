// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.KtFe10Reference
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaOrKotlinMemberDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingContext

fun KtReference.resolveToDescriptors(bindingContext: BindingContext): Collection<DeclarationDescriptor> {
    return when (this) {
        is KtFe10Reference -> resolveToDescriptors(bindingContext)
        is KtDefaultAnnotationArgumentReference -> {
            when (val declaration = resolve()) {
                is KtDeclaration -> listOfNotNull(bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration])
                is PsiMember -> listOfNotNull(declaration.getJavaOrKotlinMemberDescriptor())
                else -> emptyList()
            }
        }
        else -> {
            error("Reference $this should be KtFe10Reference but was ${this::class}")
        }
    }
}

