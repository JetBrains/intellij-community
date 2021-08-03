// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.quickfix.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.source.getPsi

class ReplaceProtectedToPublishedApiCallFix(
    element: KtExpression,
    val classOwnerPointer: SmartPsiElementPointer<KtClass>,
    val originalName: String,
    val paramNames: Map<String, String>,
    val newSignature: String,
    val isProperty: Boolean,
    val isVar: Boolean,
    val isPublishedMemberAlreadyExists: Boolean
) : KotlinQuickFixAction<KtExpression>(element) {

    override fun getFamilyName() = KotlinBundle.message("replace.with.publishedapi.bridge.call")

    override fun getText() =
        KotlinBundle.message(
            "replace.with.generated.publishedapi.bridge.call.0",
            originalName.newNameQuoted + if (!isProperty) "(...)" else ""
        )

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        if (!isPublishedMemberAlreadyExists) {
            val classOwner = classOwnerPointer.element ?: return
            val newMember: KtDeclaration =
                if (isProperty) {
                    KtPsiFactory(classOwner).createProperty(
                        "@kotlin.PublishedApi\n" +
                                "internal " + newSignature +
                                "\n" +
                                "get() = $originalName\n" +
                                if (isVar) "set(value) { $originalName = value }" else ""
                    )

                } else {
                    KtPsiFactory(classOwner).createFunction(
                        "@kotlin.PublishedApi\n" +
                                "internal " + newSignature +
                                " = $originalName(${paramNames.keys.joinToString(", ") { it }})"
                    )
                }

            ShortenReferences.DEFAULT.process(classOwner.addDeclaration(newMember))
        }
        element.replace(KtPsiFactory(element).createExpression(originalName.newNameQuoted))
    }

    companion object : KotlinSingleIntentionActionFactory() {
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
            val paramNameAndType = descriptor.valueParameters.associate { it.name.asString() to it.type.getJetTypeFqName(false) }
            val classDescriptor = descriptor.containingDeclaration as? ClassDescriptor ?: return null
            val source = classDescriptor.source.getPsi() as? KtClass ?: return null

            val newName = Name.identifier(originalName.newName)
            val contributedDescriptors = classDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered {
                it == newName
            }
            val isPublishedMemberAlreadyExists = contributedDescriptors.filterIsInstance<CallableMemberDescriptor>().any {
                signatureRenderer.render(it) == newSignature
            }

            return ReplaceProtectedToPublishedApiCallFix(
                psiElement, source.createSmartPointer(), originalName, paramNameAndType, newSignature,
                isProperty, isVar, isPublishedMemberAlreadyExists
            )
        }

        val String.newName: String
            get() = "access\$$this"

        val String.newNameQuoted: String
            get() = "`$newName`"
    }
}
