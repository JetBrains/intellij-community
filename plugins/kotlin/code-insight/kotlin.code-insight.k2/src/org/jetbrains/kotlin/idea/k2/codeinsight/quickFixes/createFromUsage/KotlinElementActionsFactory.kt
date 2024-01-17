// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

class KotlinElementActionsFactory : JvmElementActionsFactory() {
    private fun JvmClass.toKtClassOrFile(): KtElement? = if (this is JvmClassWrapperForKtClass<*>) {
        ktClassOrFile
    } else {
        when (val psi = sourceElement) {
            is KtClassOrObject -> psi
            is KtLightClassForFacade -> psi.files.firstOrNull()
            is KtLightElement<*, *> -> psi.kotlinOrigin
            is KtFile -> psi
            else -> null
        }
    }

    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        var container =
            targetClass.takeIf { (targetClass as? KtLightElementBase)?.parent?.containingFile?.virtualFile?.isWritable != false }?.toKtClassOrFile()
                ?: return emptyList()
        return when (request) {
            is CreateMethodFromKotlinUsageRequest -> {
                val isExtension = request.isExtension
                if (isExtension) {
                    container = container.containingKtFile
                }
                val actionText = CreateKotlinCallableActionTextBuilder(
                    KotlinBundle.message("text.function.0", 1),
                    request.methodName,
                    request.receiverExpression,
                    request.isAbstractClassOrInterface,
                    isExtension = isExtension,
                ).build()
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