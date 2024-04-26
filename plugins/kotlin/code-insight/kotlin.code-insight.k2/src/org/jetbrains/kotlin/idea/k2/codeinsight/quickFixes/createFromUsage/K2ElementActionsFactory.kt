// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFromUsageUtil.toKtClassOrFile
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement

class K2ElementActionsFactory : JvmElementActionsFactory() {
    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        if (targetClass is PsiElement && !BaseIntentionAction.canModify(targetClass)) return emptyList()
        var container = targetClass.toKtClassOrFile() ?: return emptyList()

        return when (request) {
            is CreateMethodFromKotlinUsageRequest -> {
                if (request.isExtension) {
                    container = container.containingKtFile
                }
                val actionText = CreateKotlinCallableActionTextBuilder.build(
                    KotlinBundle.message("text.function"), request
                )
                listOf(
                    CreateKotlinCallableAction(
                        request = request,
                        targetClass = targetClass,
                        abstract = container.isAbstractClass(),
                        needFunctionBody = !request.isAbstractClassOrInterface,
                        myText = actionText,
                        pointerToContainer = container.createSmartPointer(),
                    )
                )
            }

            else -> {
                val isContainerAbstract = container.isAbstractClass()
                listOf(
                    CreateKotlinCallableAction(
                        request = request,
                        targetClass = targetClass,
                        abstract = isContainerAbstract,
                        needFunctionBody = !isContainerAbstract && !container.isInterfaceClass(),
                        myText = KotlinBundle.message("add.method.0.to.1", request.methodName, targetClass.name.toString()),
                        pointerToContainer = container.createSmartPointer(),
                    )
                )
            }
        }
    }
}

private fun KtElement.isAbstractClass(): Boolean {
    val thisClass = this as? KtClassOrObject ?: return false
    return thisClass.isAbstract()
}