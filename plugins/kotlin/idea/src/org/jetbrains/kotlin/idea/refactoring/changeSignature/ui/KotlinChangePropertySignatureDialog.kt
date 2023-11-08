// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiCodeFragment
import com.intellij.refactoring.BaseRefactoringProcessor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.AddFullQualifierIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinBaseChangeSignatureDialog.Companion.showWarningMessage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.ui.KotlinChangeSignatureDialog.Companion.getTypeInfo
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import javax.swing.DefaultComboBoxModel

class KotlinChangePropertySignatureDialog(
    project: Project,
    private val methodDescriptor: KotlinMethodDescriptor,
    @NlsContexts.Command private val commandName: String?
) : KotlinBaseChangePropertySignatureDialog<KotlinParameterInfo, DescriptorVisibility, KotlinMethodDescriptor>(project, methodDescriptor) {

    override fun isDefaultVisibility(v: DescriptorVisibility): Boolean {
        return v == DescriptorVisibilities.PUBLIC
    }

    override fun createReturnTypeCodeFragment(m: KotlinMethodDescriptor): KtCodeFragment {
        return kotlinPsiFactory.createTypeCodeFragment(
            m.returnTypeInfo.render(),
            m.baseDeclaration,
        )
    }

    override fun createReceiverTypeCodeFragment(m: KotlinMethodDescriptor): KtCodeFragment {
        return kotlinPsiFactory.createTypeCodeFragment(
            m.receiverTypeInfo.render(),
            m.baseDeclaration,
        )
    }

    override fun PsiCodeFragment?.isValidType(): Boolean {
        return this?.getTypeInfo(false, false)?.type != null
    }

    override fun fillVisibilities(model: DefaultComboBoxModel<DescriptorVisibility>) {
        model.addAll(
            listOf(
                DescriptorVisibilities.INTERNAL,
                DescriptorVisibilities.PRIVATE,
                DescriptorVisibilities.PROTECTED,
                DescriptorVisibilities.PUBLIC,
            )
        )
    }

    private fun evaluateKotlinChangeInfo(): KotlinChangeInfo {
        val originalDescriptor = methodDescriptor.original

        val receiver = if (receiverTypeCheckBox?.isSelected == true) {
            originalDescriptor.receiver ?: KotlinParameterInfo(
                callableDescriptor = originalDescriptor.baseDescriptor,
                name = "receiver",
                defaultValueForCall = receiverDefaultValueCodeFragment.getContentElement(),
            )
        } else null

        receiver?.currentTypeInfo = receiverTypeCodeFragment.getTypeInfo(isCovariant = false, forPreview = false)
        return KotlinChangeInfo(
            originalDescriptor,
            name,
            returnTypeCodeFragment.getTypeInfo(isCovariant = false, forPreview = false),
            if (methodDescriptor.canChangeVisibility()) visibilityCombo.selectedItem as DescriptorVisibility else methodDescriptor.visibility,
            emptyList(),
            receiver,
            originalDescriptor.method,
            checkUsedParameters = true,
        )
    }

    override fun doAction() {
        val changeInfo = evaluateKotlinChangeInfo()
        val typeInfo = changeInfo.newReturnTypeInfo
        if (typeInfo.type == null && !showWarningMessage(
                myProject,
                KotlinBundle.message("message.text.property.type.cannot.be.resolved", typeInfo.render()),
            )
        ) return

        val receiverParameterInfo = changeInfo.receiverParameterInfo
        val receiverTypeInfo = receiverParameterInfo?.currentTypeInfo
        if (receiverTypeInfo != null && receiverTypeInfo.type == null && !showWarningMessage(
                myProject,
                KotlinBundle.message("message.text.property.receiver.type.cannot.be.resolved", receiverTypeInfo.render()),
            )
        ) return

        receiverParameterInfo?.let { normalizeReceiver(it, withCopy = false) }
        invokeRefactoring(KotlinChangeSignatureProcessor(myProject, changeInfo, commandName ?: title))
    }

    companion object {
        fun createProcessorForSilentRefactoring(
            project: Project,
            @NlsContexts.Command commandName: String,
            descriptor: KotlinMethodDescriptor
        ): BaseRefactoringProcessor {
            val originalDescriptor = descriptor.original
            val changeInfo = KotlinChangeInfo(methodDescriptor = originalDescriptor, context = originalDescriptor.method)
            changeInfo.newName = descriptor.name
            changeInfo.receiverParameterInfo = descriptor.receiver?.also { normalizeReceiver(it, withCopy = true) }
            return KotlinChangeSignatureProcessor(project, changeInfo, commandName)
        }

        private fun normalizeReceiver(receiver: KotlinModifiableParameterInfo, withCopy: Boolean) {
            val defaultValue = receiver.defaultValueForCall ?: return
            val newElement = if (withCopy) {
                val fragment = KtPsiFactory(defaultValue.project).createExpressionCodeFragment(defaultValue.text, defaultValue)
                fragment.getContentElement() ?: return
            } else {
                defaultValue
            }

            receiver.defaultValueForCall = AddFullQualifierIntention.Holder.addQualifiersRecursively(newElement) as? KtExpression
        }
    }
}