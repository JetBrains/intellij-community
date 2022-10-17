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
            return buildString {
                append(KotlinBundle.message("text.add"))
                if (propertyInfo.isLateinitPreferred || propertyInfo.modifierList?.hasModifier(KtTokens.LATEINIT_KEYWORD) == true) {
                    append("lateinit ")
                } else if (propertyInfo.isConst) {
                    append("const ")
                }
                append(if (propertyInfo.writable) "var" else "val")
                append(KotlinBundle.message("property.0.to.1", propertyInfo.name, classOrFileName.toString()))
            }
        }

    override fun getFamilyName(): String = KotlinBundle.message("add.property")
}