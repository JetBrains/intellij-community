// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit

class Fe10KotlinNameSuggestionProvider: KotlinNameSuggestionProvider<Fe10KotlinNewDeclarationNameValidator>() {
    override val nameSuggester: Fe10KotlinNameSuggester
        get() = Fe10KotlinNameSuggester

    override fun createNameValidator(
        container: PsiElement,
        anchor: PsiElement?,
        target: ValidatorTarget,
        excludedDeclarations: List<KtDeclaration>
    ): Fe10KotlinNewDeclarationNameValidator {
        return Fe10KotlinNewDeclarationNameValidator(container, anchor, target, excludedDeclarations)
    }

    override fun getReturnTypeNames(callable: KtCallableDeclaration, validator: Fe10KotlinNewDeclarationNameValidator): List<String> {
        val callableDescriptor = callable.unsafeResolveToDescriptor(BodyResolveMode.PARTIAL) as CallableDescriptor
        val type = callableDescriptor.returnType
        if (type != null && !type.isUnit() && !KotlinBuiltIns.isPrimitiveType(type)) {
            return nameSuggester.suggestNamesByType(type, validator)
        }

        return emptyList()
    }

    override fun getNameForArgument(argument: KtValueArgument): String? {
        val callElement = (argument.parent as? KtValueArgumentList)?.parent as? KtCallElement ?: return null
        val resolvedCall = callElement.resolveToCall() ?: return null
        return (resolvedCall.getArgumentMapping(argument) as? ArgumentMatch)?.valueParameter?.name?.asString()
    }
}