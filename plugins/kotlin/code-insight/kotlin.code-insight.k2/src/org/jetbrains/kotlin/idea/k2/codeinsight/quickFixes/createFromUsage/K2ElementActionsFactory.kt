// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PropertyUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.toKtClassOrFile
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierListOwner

class K2ElementActionsFactory : JvmElementActionsFactory() {
    override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
        val kModifierOwner = target.sourceElement?.unwrapped as? KtModifierListOwner ?: return emptyList()

        if (request.modifier == JvmModifier.FINAL && !request.shouldBePresent()) {
            return listOf(
                AddModifierFix(kModifierOwner, KtTokens.OPEN_KEYWORD)
            )
        }
        return emptyList()
    }

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

    override fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
        val declaration = ((target as? KtLightElement<*, *>)?.kotlinOrigin as? KtModifierListOwner)?.takeIf {
            it.language == KotlinLanguage.INSTANCE
        } ?: return emptyList()

        val annotationUseSiteTarget = when (target) {
            is JvmField -> AnnotationUseSiteTarget.FIELD
            is JvmMethod -> when {
                PropertyUtil.isSimplePropertySetter(target as? PsiMethod) -> AnnotationUseSiteTarget.PROPERTY_SETTER
                PropertyUtil.isSimplePropertyGetter(target as? PsiMethod) -> AnnotationUseSiteTarget.PROPERTY_GETTER
                else -> null
            }

            else -> null
        }
        return listOfNotNull(K2CreatePropertyFromUsageBuilder.generateAnnotationAction(declaration, annotationUseSiteTarget, request))
    }

    override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
        val targetContainer = targetClass.toKtClassOrFile() ?: return emptyList()

        val writable = JvmModifier.FINAL !in request.modifiers && !request.isConstant

        val action = K2CreatePropertyFromUsageBuilder.generatePropertyAction(
            targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = false
        )

        val actions = if (writable) {
            listOfNotNull(
                action,
                K2CreatePropertyFromUsageBuilder.generatePropertyAction(
                    targetContainer = targetContainer, classOrFileName = targetClass.name, request = request, lateinit = true
                )
            )
        } else {
            listOfNotNull(action)
        }
        return actions
    }
}

private fun KtElement.isAbstractClass(): Boolean {
    val thisClass = this as? KtClassOrObject ?: return false
    return thisClass.isAbstract()
}