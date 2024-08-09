// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.source.getPsi

internal object ReplaceProtectedToPublishedApiCallFixFactory : KotlinSingleIntentionActionFactory()  {
    private val signatureRenderer = IdeDescriptorRenderers.SOURCE_CODE.withOptions {
        defaultParameterValueRenderer = null
        startFromDeclarationKeyword = true
        withoutReturnType = true
    }

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val psiElement = diagnostic.psiElement as? KtExpression ?: return null
        val descriptor = DiagnosticFactory.cast(
            diagnostic, Errors.PROTECTED_CALL_FROM_PUBLIC_INLINE.warningFactory, Errors.PROTECTED_CALL_FROM_PUBLIC_INLINE.errorFactory
        ).a.let {
            if (it is CallableMemberDescriptor) DescriptorUtils.getDirectMember(it) else it
        }
        val isProperty = descriptor is PropertyDescriptor
        val isVar = descriptor is PropertyDescriptor && descriptor.isVar

        val signature = signatureRenderer.render(descriptor)
        val originalName = descriptor.name.asString()
        val newSignature =
            if (isProperty) {
                signature.replaceFirst("$originalName:", "${originalName.newNameQuoted}:")
            } else {
                signature.replaceFirst("$originalName(", "${originalName.newNameQuoted}(")
            }
        val paramNameAndType = descriptor.valueParameters.map { it.name.asString() }
        val classDescriptor = descriptor.containingDeclaration as? ClassDescriptor ?: return null
        val source = classDescriptor.source.getPsi() as? KtClass ?: return null
        if (source != psiElement.containingClass()) return null

        val newName = Name.identifier(originalName.newName)
        val contributedDescriptors = classDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered {
            it == newName
        }
        val isPublishedMemberAlreadyExists = contributedDescriptors.filterIsInstance<CallableMemberDescriptor>().any {
            signatureRenderer.render(it) == newSignature
        }

        return ReplaceProtectedToPublishedApiCallFix(
            psiElement, originalName, paramNameAndType, newSignature,
            isProperty, isVar, isPublishedMemberAlreadyExists
        ).asIntention()
    }

    val String.newName: String
        get() = "access\$$this"

    val String.newNameQuoted: String
        get() = "`$newName`"
}