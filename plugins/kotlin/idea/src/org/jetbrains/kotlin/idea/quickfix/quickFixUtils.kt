// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.cfg.containingDeclarationForPseudocode
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

fun KtElement.containingFunction(): KtNamedFunction? {
    val containingDeclaration = containingDeclarationForPseudocode
    return if (containingDeclaration is KtFunctionLiteral) {
        val call = containingDeclaration.getStrictParentOfType<KtCallExpression>()
        if (call?.resolveToCall()?.resultingDescriptor?.isInlineOrInsideInline() == true) {
            containingDeclaration.containingFunction()
        } else {
            null
        }
    } else {
        containingDeclaration as? KtNamedFunction
    }
}

private fun DeclarationDescriptor.isInlineOrInsideInline(): Boolean =
    getInlineCallSiteVisibility() != null

private fun DeclarationDescriptor.getInlineCallSiteVisibility(): DescriptorVisibility? {
    var declaration: DeclarationDescriptor? = this
    var result: DescriptorVisibility? = null
    while (declaration != null) {
        if (declaration is FunctionDescriptor && declaration.isInline) {
            if (!DescriptorVisibilities.isPrivate(declaration.visibility)) {
                return declaration.visibility
            }
            result = declaration.visibility
        }
        declaration = declaration.containingDeclaration
    }
    return result
}
