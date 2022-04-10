// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.quickfix.KotlinIntentionActionFactoryWithDelegate
import org.jetbrains.kotlin.idea.quickfix.QuickFixWithDelegateFactory
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.storage.StorageManager

object CreateTypeParameterUnmatchedTypeArgumentActionFactory :
    KotlinIntentionActionFactoryWithDelegate<KtTypeArgumentList, CreateTypeParameterData>() {
    override fun getElementOfInterest(diagnostic: Diagnostic) = diagnostic.psiElement as? KtTypeArgumentList

    @OptIn(FrontendInternals::class)
    override fun extractFixData(element: KtTypeArgumentList, diagnostic: Diagnostic): CreateTypeParameterData? {
        val project = element.project
        val typeArguments = element.arguments
        val context = element.analyze()
        val referencedDescriptor = when (val parent = element.parent) {
            is KtUserType -> context[BindingContext.REFERENCE_TARGET, parent.referenceExpression]
            is KtCallElement -> parent.getResolvedCall(context)?.resultingDescriptor
            else -> null
        } ?: return null
        val referencedDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, referencedDescriptor) as? KtTypeParameterListOwner
            ?: return null
        if (!referencedDeclaration.canRefactor()) return null

        val missingParameterCount = typeArguments.size - referencedDeclaration.typeParameters.size
        if (missingParameterCount <= 0) return null

        val scope = referencedDeclaration.getResolutionScope()
        val suggestedNames = KotlinNameSuggester.suggestNamesForTypeParameters(
            missingParameterCount,
            CollectingNameValidator(referencedDeclaration.typeParameters.mapNotNull { it.name }) {
                scope.findClassifier(Name.identifier(it), NoLookupLocation.FROM_IDE) == null
            }
        )
        val storageManager = element.getResolutionFacade().frontendService<StorageManager>()
        val typeParameterInfos = suggestedNames.map { name ->
            TypeParameterInfo(
                name,
                null,
                createFakeTypeParameterDescriptor(referencedDescriptor, name, storageManager)
            )
        }
        return CreateTypeParameterData(referencedDeclaration, typeParameterInfos)
    }

    override fun createFixes(
        originalElementPointer: SmartPsiElementPointer<KtTypeArgumentList>,
        diagnostic: Diagnostic,
        quickFixDataFactory: (KtTypeArgumentList) -> CreateTypeParameterData?
    ): List<QuickFixWithDelegateFactory> {
        return QuickFixWithDelegateFactory factory@{
            val originalElement = originalElementPointer.element ?: return@factory null
            val data = quickFixDataFactory(originalElement) ?: return@factory null
            CreateTypeParameterFromUsageFix(originalElement, data, false)
        }.let(::listOf)
    }
}