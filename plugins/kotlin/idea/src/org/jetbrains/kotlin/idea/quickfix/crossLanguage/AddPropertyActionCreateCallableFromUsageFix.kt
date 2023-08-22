// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.PropertyInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.util.resolveToKotlinType
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.types.Variance

class AddPropertyActionCreateCallableFromUsageFix(
    targetContainer: KtElement,
    modifierList: KtModifierList,
    val propertyType: JvmType,
    val propertyName: String,
    val setterRequired: Boolean,
    val isLateinitPreferred: Boolean = setterRequired,
    classOrFileName: String?
) : AbstractPropertyActionCreateCallableFromUsageFix(targetContainer, classOrFileName) {
    private val modifierListPointer = modifierList.createSmartPointer()

    init {
        init()
    }

    override val propertyInfo: PropertyInfo?
        get() = run {
            val targetContainer = element ?: return@run null
            val modifierList = modifierListPointer.element ?: return@run null
            val resolutionFacade = targetContainer.getResolutionFacade()
            val nullableAnyType = resolutionFacade.moduleDescriptor.builtIns.nullableAnyType
            val initializer = if(!isLateinitPreferred) {
                KtPsiFactory(targetContainer.project).createExpression("TODO(\"initialize me\")")
            } else null
            val ktType = (propertyType as? PsiType)?.resolveToKotlinType(resolutionFacade) ?: nullableAnyType
            val propertyInfo = PropertyInfo(
                propertyName,
                TypeInfo.Empty,
                TypeInfo(ktType, Variance.INVARIANT),
                setterRequired,
                listOf(targetContainer),
                modifierList = modifierList,
                initializer = initializer,
                isLateinitPreferred = isLateinitPreferred
            )
            propertyInfo
        }

}
