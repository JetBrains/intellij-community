// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureConfiguration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.modify
import org.jetbrains.kotlin.idea.refactoring.changeSignature.runChangeSignature
import org.jetbrains.kotlin.idea.refactoring.resolveToExpectedDescriptorIfPossible
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ConvertParameterToReceiverIntention : SelfTargetingIntention<KtParameter>(
    KtParameter::class.java,
    KotlinBundle.messagePointer("convert.parameter.to.receiver")
) {
    override fun isApplicableTo(element: KtParameter, caretOffset: Int): Boolean {
        val identifier = element.nameIdentifier ?: return false
        if (!identifier.textRange.containsOffset(caretOffset)) return false

        if (element.isVarArg) return false

        val function = element.ownerFunction as? KtNamedFunction ?: return false
        if (function.receiverTypeReference != null) return false
        if (function.overridesJavaMethod()) return false

        return true
    }

    private fun KtNamedFunction.overridesJavaMethod(): Boolean {
        if (!hasModifier(OVERRIDE_KEYWORD)) return false
        val functionDescriptor = resolveToDescriptorIfAny() ?: return false
        val baseDescriptor = functionDescriptor.original.overriddenDescriptors.firstOrNull() ?: return false
        return baseDescriptor is JavaCallableMemberDescriptor
    }

    private fun configureChangeSignature(parameterIndex: Int): KotlinChangeSignatureConfiguration =
        object : KotlinChangeSignatureConfiguration {
            override fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor {
                return originalDescriptor.modify { it.receiver = originalDescriptor.parameters[parameterIndex] }
            }

            override fun isPerformSilently(affectedFunctions: Collection<PsiElement>) = true
        }

    override fun startInWriteAction() = false

    override fun applyTo(element: KtParameter, editor: Editor?) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val parameterIndex = function.valueParameters.indexOf(element)
        val descriptor = function.resolveToExpectedDescriptorIfPossible() as? FunctionDescriptor ?: return
        runChangeSignature(element.project, editor, descriptor, configureChangeSignature(parameterIndex), element, text)
    }
}
