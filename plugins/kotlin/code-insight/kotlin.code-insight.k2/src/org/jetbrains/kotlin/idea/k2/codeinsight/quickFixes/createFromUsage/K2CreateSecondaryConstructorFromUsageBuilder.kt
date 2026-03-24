// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.expectedType
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

object K2CreateSecondaryConstructorFromUsageBuilder {
    fun buildRequestsAndActions(call: KtCallElement): List<IntentionAction> {
        if (call is KtCallExpression && call.typeArguments.isNotEmpty()) return emptyList()

        val targetClass = analyze(call) { findTargetClass(call) } ?: return emptyList()
        if (!targetClass.canRefactorElement()) return emptyList()

        if (!analyze(call) { isExpectedTypeCompatible(call) }) return emptyList()

        val lightClass = targetClass as? PsiClass ?: (targetClass as? KtClass)?.toLightClass() ?: return emptyList()
        val request = CreateConstructorFromKotlinUsageRequest(call, listOf(JvmModifier.PUBLIC))

        return EP_NAME.extensions.flatMap { ext ->
            ext.createAddConstructorActions(lightClass, request)
        }
    }

    context(_: KaSession)
    private fun findTargetClass(call: KtCallElement): PsiElement? {
        return when (call) {
            is KtCallExpression -> findTargetClassForCallExpression(call)
            is KtConstructorDelegationCall -> findTargetClassForDelegationCall(call)
            is KtSuperTypeCallEntry -> findTargetClassForSuperTypeEntry(call)
            else -> null
        }
    }

    internal fun findTargetClassForCallExpression(call: KtCallExpression): PsiElement? {
        val resolved = call.referenceExpression()?.mainReference?.multiResolve(true)?.find { ref ->
            val element = ref.element
            (element as? PsiMethod)?.isConstructor == true || element is PsiClass || element is KtClass || element is KtConstructor<*>
        }?.element
        return when (resolved) {
            is KtConstructor<*> -> resolved.getContainingClassOrObject() as? KtClass
            is KtClass -> resolved
            is PsiMethod -> resolved.containingClass
            is PsiClass -> resolved
            else -> null
        }
    }

    context(a: KaSession)
    private fun findTargetClassForDelegationCall(call: KtConstructorDelegationCall): PsiElement? {
        val constructor = PsiTreeUtil.getParentOfType(call, KtConstructor::class.java) ?: return null
        val containingClass = constructor.getContainingClassOrObject() as? KtClass ?: return null

        return if (call.isCallToThis) {
            containingClass
        } else {
            findSuperClass(containingClass)
        }
    }

    context(_: KaSession)
    private fun findTargetClassForSuperTypeEntry(call: KtSuperTypeCallEntry): PsiElement? {
        val typeRef = call.typeReference ?: return null
        val classSymbol = typeRef.type.expandedSymbol ?: return null
        if (classSymbol.classKind == KaClassKind.INTERFACE) return null
        val psi = classSymbol.psi
        return psi as? KtClass ?: psi as? PsiClass
    }

    context(_: KaSession)
    private fun findSuperClass(ktClass: KtClass): PsiElement? {
        val classSymbol = ktClass.symbol as? KaClassSymbol ?: return null
        for (superType in classSymbol.superTypes) {
            val superSymbol = superType.expandedSymbol ?: continue
            if (superSymbol.classKind != KaClassKind.INTERFACE) {
                val element = superSymbol.psi
                return element as? KtClass ?: element as? PsiClass
            }
        }
        return null
    }

    context(_: KaSession)
    private fun isExpectedTypeCompatible(call: KtCallElement): Boolean {
        if (call !is KtCallExpression) return true
        val expectedType = call.expectedType ?: return true
        val constructedType = call.expressionType ?: return true
        return constructedType.isSubtypeOf(expectedType)
    }
}
