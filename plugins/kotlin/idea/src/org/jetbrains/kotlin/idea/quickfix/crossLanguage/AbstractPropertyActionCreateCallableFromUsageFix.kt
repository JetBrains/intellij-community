// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix.crossLanguage

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.PropertyInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFixBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractPropertyActionCreateCallableFromUsageFix(
  targetContainer: KtElement,
  private val classOrFileName: String?
) : CreateCallableFromUsageFixBase<KtElement>(targetContainer, false) {

    protected abstract val propertyInfo: PropertyInfo?

    override val callableInfo: PropertyInfo?
        get() = propertyInfo

    override val calculatedText: String
        get() {
            val propertyInfo = callableInfos.first() as PropertyInfo
            val modifier = if (propertyInfo.isLateinitPreferred || propertyInfo.modifierList?.hasModifier(KtTokens.LATEINIT_KEYWORD) == true) {
                "lateinit "
            } else if (propertyInfo.isConst) {
                "const "
            } else ""
            val property = if (propertyInfo.writable) "var" else "val"
            return KotlinBundle.message("quickFix.add.property.text", modifier, property, propertyInfo.name, classOrFileName.toString())
        }

    override fun getFamilyName(): String = KotlinBundle.message("quickfix.add.property.familyName")
}