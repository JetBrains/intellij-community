// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.idea.refactoring.canRefactor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns

object CreateParameterByNamedArgumentActionFactory : CreateParameterFromUsageFactory<KtValueArgument>() {
    override fun getElementOfInterest(diagnostic: Diagnostic): KtValueArgument? {
        return diagnostic.psiElement.findParentOfType<KtValueArgument>(strict = false)?.takeIf { it.isNamed() }
    }

    override fun extractFixData(element: KtValueArgument, diagnostic: Diagnostic): CreateParameterData<KtValueArgument>? {
        val result = (diagnostic.psiFile as? KtFile)?.analyzeWithAllCompilerChecks() ?: return null
        val context = result.bindingContext

        val name = element.getArgumentName()?.text ?: return null
        val argumentExpression = element.getArgumentExpression()

        val callElement = element.getStrictParentOfType<KtCallElement>() ?: return null
        val functionDescriptor = callElement.getResolvedCall(context)?.resultingDescriptor as? FunctionDescriptor ?: return null
        val callable = DescriptorToSourceUtilsIde.getAnyDeclaration(callElement.project, functionDescriptor) ?: return null
        if (!((callable is KtFunction || callable is KtClass) && callable.canRefactor())) return null

        val anyType = functionDescriptor.builtIns.anyType
        val paramType = argumentExpression?.guessTypes(context, result.moduleDescriptor)?.let {
            when (it.size) {
                0 -> anyType
                1 -> it.first()
                else -> return null
            }
        } ?: anyType
        if (paramType.hasTypeParametersToAdd(functionDescriptor, context)) return null

        val parameterInfo = KotlinParameterInfo(
            callableDescriptor = functionDescriptor,
            name = name,
            originalTypeInfo = KotlinTypeInfo(false, paramType),
            defaultValueForCall = argumentExpression,
            valOrVar = if (callable.needVal()) KotlinValVar.Val else KotlinValVar.None
        )

        return CreateParameterData(parameterInfo, element)
    }

    private fun PsiElement.needVal(): Boolean {
        return when (this) {
            is KtPrimaryConstructor -> containingClass()?.let { it.isData() || it.isAnnotation() } == true
            is KtClass -> isAnnotation()
            else -> false
        }
    }
}
