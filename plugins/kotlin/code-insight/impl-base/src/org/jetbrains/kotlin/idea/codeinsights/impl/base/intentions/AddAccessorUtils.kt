// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object AddAccessorUtils {
    fun familyAndActionName(addGetter: Boolean, addSetter: Boolean): @IntentionFamilyName String = when {
        addGetter && addSetter -> KotlinBundle.message("text.add.getter.and.setter")
        addGetter -> KotlinBundle.message("text.add.getter")
        addSetter -> KotlinBundle.message("text.add.setter")
        else -> throw AssertionError("At least one from (addGetter, addSetter) should be true")
    }

    fun addAccessors(element: KtProperty, addGetter: Boolean, addSetter: Boolean, caretMover: ((Int) -> Unit)?) {
        val hasInitializer = element.hasInitializer()
        val psiFactory = KtPsiFactory(element)
        if (addGetter) {
            val expression = if (hasInitializer) psiFactory.createExpression("field") else psiFactory.createBlock("TODO()")
            val getter = psiFactory.createPropertyGetter(expression)
            val added = if (element.setter != null) {
                element.addBefore(getter, element.setter)
            } else {
                element.add(getter)
            }.reformatted(canChangeWhiteSpacesOnly = true)
            if (!hasInitializer) {
                (added as? KtPropertyAccessor)?.bodyBlockExpression?.statements?.firstOrNull()?.let {
                    caretMover?.invoke(it.startOffset)
                }
            }
        }
        if (addSetter) {
            val expression = if (hasInitializer) psiFactory.createBlock("field = value") else psiFactory.createEmptyBody()
            val setter = psiFactory.createPropertySetter(expression)
            val added = element.add(setter).reformatted(canChangeWhiteSpacesOnly = true)
            if (!hasInitializer && !addGetter) {
                (added as? KtPropertyAccessor)?.bodyBlockExpression?.lBrace?.let {
                    caretMover?.invoke(it.startOffset + 1)
                }
            }
        }
    }
}