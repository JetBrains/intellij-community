// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class KotlinElementActionsFactory : JvmElementActionsFactory() {
    private fun JvmClass.toKtClassOrFile(): KtElement? = when (val psi = sourceElement) {
        is KtClassOrObject -> psi
        is KtLightClassForFacade -> psi.files.firstOrNull()
        is KtLightElement<*, *> -> psi.kotlinOrigin
        is KtFile -> psi
        else -> null
    }

    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        var container = targetClass.toKtClassOrFile() ?: return emptyList()
        val requestForCreateKtCallable = request as? CreateKotlinCallableFromKotlinUsageRequest ?: return emptyList()
        val isExtension = requestForCreateKtCallable.isExtension
        if (isExtension) {
            container = container.containingKtFile
        }
        val actionText = CreateKotlinCallableActionTextBuilder(
            KotlinBundle.message("text.function.0", 1),
            requestForCreateKtCallable.methodName,
            requestForCreateKtCallable.receiverExpression,
            requestForCreateKtCallable.isAbstractClassOrInterface,
            isExtension = isExtension,
        ).build()
        return listOf(CreateKotlinCallableAction(
            request = request,
            targetClass = targetClass,
            abstract = container.isAbstractClass(),
            needFunctionBody = !requestForCreateKtCallable.isAbstractClassOrInterface,
            myText = actionText,
            pointerToContainer = container.createSmartPointer(),
        ))
    }
}

private fun KtElement.isAbstractClass(): Boolean {
    val thisClass = this as? KtClassOrObject ?: return false
    return thisClass.isAbstract()
}