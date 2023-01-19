// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.psi.JvmPsiConversionHelper
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.ConstructorInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.ParameterInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.AbstractCreateCallableFromUsageFixWithTextAndFamilyName
import org.jetbrains.kotlin.idea.util.resolveToKotlinType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddConstructorCreateCallableFromUsageFix(
    private val request: CreateConstructorRequest,
    modifierList: KtModifierList,
    providedText: String,
    @Nls familyName: String,
    targetKtClass: KtClass
) : AbstractCreateCallableFromUsageFixWithTextAndFamilyName<KtClass>(
    providedText = providedText,
    familyName = familyName,
    originalExpression = targetKtClass
) {
    private val modifierListPointer = modifierList.createSmartPointer()

    init {
        init()
    }

    override val callableInfo: ConstructorInfo?
        get() = run {
            val targetKtClass = element.safeAs<KtClass>() ?: return@run null
            val modifierList = modifierListPointer.element ?: return@run null
            val resolutionFacade = targetKtClass.getResolutionFacade()
            val nullableAnyType = resolutionFacade.moduleDescriptor.builtIns.nullableAnyType
            val helper = JvmPsiConversionHelper.getInstance(targetKtClass.project)
            val parameters = request.expectedParameters
            val parameterInfos = parameters.mapIndexed { index, param ->
                val ktType =
                    param.expectedTypes.firstOrNull()?.theType?.let { helper.convertType(it).resolveToKotlinType(resolutionFacade) }
                        ?: nullableAnyType
                val name = param.semanticNames.firstOrNull() ?: "arg${index + 1}"
                ParameterInfo(TypeInfo(ktType, Variance.IN_VARIANCE), listOf(name))
            }
            val needPrimary = !targetKtClass.hasExplicitPrimaryConstructor()
            val ktFactory = KtPsiFactory(targetKtClass)
            val annotations = request.annotations.map { ktFactory.createAnnotationEntry("@${it.qualifiedName}") }
            val constructorInfo = ConstructorInfo(
                parameterInfos,
                targetKtClass,
                isPrimary = needPrimary,
                modifierList = modifierList,
                withBody = true,
                annotations = annotations
            )
            constructorInfo
        }
}