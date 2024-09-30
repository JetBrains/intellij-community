// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.psi.createSmartPointer
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.FunctionInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.ParameterInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.AbstractCreateCallableFromUsageFixWithTextAndFamilyName
import org.jetbrains.kotlin.idea.quickfix.crossLanguage.KotlinElementActionsFactory.Companion.toKotlinTypeInfo
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddMethodCreateCallableFromUsageFix(
    private val request: CreateMethodRequest,
    modifierList: KtModifierList,
    providedText: String,
    @Nls familyName: String,
    targetContainer: KtElement,
    val annotations: List<AnnotationRequest> = emptyList(),
) : AbstractCreateCallableFromUsageFixWithTextAndFamilyName<KtElement>(
    providedText = providedText,
    familyName = familyName,
    originalExpression = targetContainer
) {
    private val modifierListPointer = modifierList.createSmartPointer()

    init {
        init()
    }

    override val callableInfo: FunctionInfo?
        get() = run {
            val targetContainer = element ?: return@run null
            val modifierList = modifierListPointer.element ?: return@run null
            val resolutionFacade = KotlinCacheService.getInstance(targetContainer.project)
                .getResolutionFacadeByFile(targetContainer.containingFile, JvmPlatforms.unspecifiedJvmPlatform)
                ?: return null
            val returnTypeInfo = request.returnType.toKotlinTypeInfo(resolutionFacade)
            val parameters = request.expectedParameters
            val parameterInfos = parameters.map { parameter ->
                ParameterInfo(parameter.expectedTypes.toKotlinTypeInfo(resolutionFacade), parameter.semanticNames.toList())
            }
            val psiFactory = KtPsiFactory(targetContainer.project)
            val annotations = annotations.map {
                psiFactory.createAnnotationEntry("@${renderAnnotation(targetContainer, it, psiFactory)}")
            }
            val methodName = request.methodName
            FunctionInfo(
                methodName,
                TypeInfo.Empty,
                returnTypeInfo,
                listOf(targetContainer),
                parameterInfos,
                isForCompanion = JvmModifier.STATIC in request.modifiers,
                modifierList = modifierList,
                preferEmptyBody = true,
                annotations = annotations,
                elementToReplace = request.elementToReplace
            )
        }

    override fun isStartTemplate(): Boolean = request.isStartTemplate
}