// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k1.macro

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.liveTemplates.macro.AbstractAnonymousSuperMacro
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.utils.collectDescriptorsFiltered

internal class Fe10AnonymousSuperMacro : AbstractAnonymousSuperMacro() {
    override fun resolveSupertypes(expression: KtExpression, file: KtFile): Collection<PsiNamedElement> {
        val bindingContext = expression.analyze(BodyResolveMode.FULL)
        val resolutionScope = expression.getResolutionScope(bindingContext, expression.getResolutionFacade())

        return resolutionScope.collectDescriptorsFiltered(DescriptorKindFilter.NON_SINGLETON_CLASSIFIERS)
            .filter {
                it is ClassDescriptor &&
                        (it.modality == Modality.OPEN || it.modality == Modality.ABSTRACT) &&
                        (it.kind == ClassKind.CLASS || it.kind == ClassKind.INTERFACE)
            }
            .mapNotNull { DescriptorToSourceUtils.descriptorToDeclaration(it) as PsiNamedElement? }
    }
}