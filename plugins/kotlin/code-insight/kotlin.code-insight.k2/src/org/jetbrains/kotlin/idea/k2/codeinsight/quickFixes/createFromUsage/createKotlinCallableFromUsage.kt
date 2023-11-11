// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
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
        val request = CreateKotlinCallableFromKotlinUsageRequest(myCall, mutableSetOf())
        val parent = myCall.parent
        if (parent is KtDotQualifiedExpression) {
            analyze(myCall) {
                val call = parent.receiverExpression.resolveCall()
                val symbol = (call?.singleFunctionCallOrNull() ?: call?.singleVariableAccessCall())?.partiallyAppliedSymbol
                if (symbol != null) {
                    val classSymbol = symbol.symbol.returnType.expandedClassSymbol ?: return myRequests
                    (classSymbol.psi as? KtClassOrObject)?.toLightClass()?.let { klass ->
                        myRequests[klass] = request
                    }
                    classSymbol.superTypes.forEach { ktSuperType ->
                        val superClass = ktSuperType.expandedClassSymbol ?: return@forEach
                        if (superClass.origin == KtSymbolOrigin.SOURCE) {
                            (superClass.psi as? KtClassOrObject)?.toLightClass()
                                ?.let { myRequests[it] = request }
                        }
                    }
                }
            }
        } else {
            val lightClass = myCall.parentOfType<KtClassOrObject>()?.toLightClass()
            if (lightClass != null) {
                myRequests[lightClass] = request
                InheritanceUtil.getSuperClasses(lightClass).forEach { superClass ->
                    if (lightClass.manager.isInProject(superClass)) {
                        myRequests[superClass] = request
                    }
                }
            }
        }

        return myRequests
    }
}