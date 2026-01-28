// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateParameterUtil
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getExpressionForTypeGuess
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getTypeParameters
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType

@K1Deprecation
object CreateParameterByRefActionFactory : CreateParameterFromUsageFactory<KtSimpleNameExpression>() {
    override fun getElementOfInterest(diagnostic: Diagnostic): KtSimpleNameExpression? {
        val refExpr = diagnostic.psiElement.findParentOfType<KtNameReferenceExpression>(strict = false) ?: return null
        if (refExpr.getQualifiedElement() != refExpr) return null
        if (refExpr.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) return null
        return refExpr
    }

    fun extractFixData(element: KtSimpleNameExpression): CreateParameterData<KtSimpleNameExpression>? {
        val (context, moduleDescriptor) = (element.containingFile as? KtFile)?.analyzeWithAllCompilerChecks() ?: return null

        val varExpected = element.getAssignmentByLHS() != null

        val paramType = element.getExpressionForTypeGuess().guessTypes(context, moduleDescriptor).let {
            when (it.size) {
                0 -> moduleDescriptor.builtIns.anyType
                1 -> it.first()
                else -> return null
            }
        }


        fun chooseFunction(): PsiElement? {
            if (varExpected) return null
            return element.parents.filter { it is KtNamedFunction || it is KtSecondaryConstructor }.firstOrNull()
        }

        val pair = CreateParameterUtil.chooseContainerPreferringClass(element, varExpected)
        val container = pair.first ?: chooseFunction()
        val valOrVar: KotlinValVar = when (pair.second) {
            CreateParameterUtil.ValVar.VAL -> KotlinValVar.Val
            CreateParameterUtil.ValVar.VAR -> KotlinValVar.Var
            CreateParameterUtil.ValVar.NONE -> KotlinValVar.None
        }

        val functionDescriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, container]?.let {
            if (it is ClassDescriptor) it.unsubstitutedPrimaryConstructor else it
        } as? FunctionDescriptor ?: return null

        if (paramType.hasTypeParametersToAdd(functionDescriptor, context)) return null

        return CreateParameterData(
            KotlinParameterInfo(
                callableDescriptor = functionDescriptor,
                name = element.getReferencedName(),
                originalTypeInfo = KotlinTypeInfo(false, paramType),
                valOrVar = valOrVar
            ),
            element
        )
    }

    override fun extractFixData(element: KtSimpleNameExpression, diagnostic: Diagnostic): CreateParameterData<KtSimpleNameExpression>? = extractFixData(element)
}

@K1Deprecation
fun KotlinType.hasTypeParametersToAdd(functionDescriptor: FunctionDescriptor, context: BindingContext): Boolean {
    val typeParametersToAdd = LinkedHashSet(getTypeParameters())
    typeParametersToAdd.removeAll(functionDescriptor.typeParameters)
    if (typeParametersToAdd.isEmpty()) return false

    val scope = when (functionDescriptor) {
        is ConstructorDescriptor -> {
            (functionDescriptor.containingDeclaration as? ClassDescriptorWithResolutionScopes)?.scopeForClassHeaderResolution
        }

        else -> {
            val function = functionDescriptor.source.getPsi() as? KtFunction
            function?.bodyExpression?.getResolutionScope(context, function.getResolutionFacade())
        }
    } ?: return true

    return typeParametersToAdd.any { scope.findClassifier(it.name, NoLookupLocation.FROM_IDE) != it }
}
