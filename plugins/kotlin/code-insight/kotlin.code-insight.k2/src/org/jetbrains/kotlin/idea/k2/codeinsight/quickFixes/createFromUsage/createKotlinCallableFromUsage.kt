// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

fun generateCreateKotlinCallableActions(call: KtCallExpression): List<IntentionAction> {
    val methodRequests = MethodRequestsBuilder(call).buildRequests()
    val extensions = EP_NAME.extensions
    return methodRequests.flatMap { (clazz, request) ->
        extensions.flatMap { ext ->
            ext.createAddMethodActions(clazz, request)
        }
    }.groupActionsByType(KotlinLanguage.INSTANCE)
}

class MethodRequestsBuilder(private val myCall: KtCallExpression) {

    private val myRequests = LinkedHashMap<JvmClass, CreateMethodRequest>()

    fun buildRequests(): Map<JvmClass, CreateMethodRequest> {
        val parent = myCall.parent
        val manager = myCall.manager

        val targetClasses = mutableSetOf<JvmClass>()

        fun addTargetClass(psi: PsiElement?) {
            val jvmClass = (psi as? KtClassOrObject)?.toLightClass() ?: psi as? JvmClass
            if (jvmClass is PsiElement && manager.isInProject(jvmClass)) {
                targetClasses.add(jvmClass)
            }
        }
        val request = CreateKotlinCallableFromKotlinUsageRequest(myCall, mutableSetOf<JvmModifier>())
        if (parent is KtDotQualifiedExpression) {
            analyze(myCall) {
                val call = parent.receiverExpression.resolveCall()
                val symbol = call?.singleCallOrNull<KtCallableMemberCall<*,*>>()?.partiallyAppliedSymbol
                if (symbol != null) {
                    val classSymbol = symbol.symbol.returnType.expandedClassSymbol ?: return myRequests
                    addTargetClass(classSymbol.psi)
                    classSymbol.superTypes.forEach { ktSuperType ->
                        addTargetClass(ktSuperType.expandedClassSymbol?.psi)
                    }
                }
            }
        } else {
            val lightClass = myCall.parentOfType<KtClassOrObject>()?.toLightClass()
            if (lightClass != null) {
                InheritanceUtil.getSuperClasses(lightClass).forEach { superClass ->
                    addTargetClass(superClass)
                }
            }
        }

        targetClasses.forEach {
            myRequests[it] = request
        }

        return myRequests
    }
}