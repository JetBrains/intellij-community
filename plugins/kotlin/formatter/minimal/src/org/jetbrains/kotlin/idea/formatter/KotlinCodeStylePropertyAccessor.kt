// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.codeStyle.properties.CodeStyleChoiceList
import com.intellij.application.options.codeStyle.properties.CodeStylePropertyAccessor
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings

class KotlinCodeStylePropertyAccessor(private val kotlinCodeStyle: KotlinCodeStyleSettings) :
    CodeStylePropertyAccessor<String>(),
    CodeStyleChoiceList {
    override fun set(extVal: String): Boolean = applyKotlinCodeStyle(extVal, kotlinCodeStyle.container)
    override fun get(): String? = kotlinCodeStyle.container.kotlinCodeStyleDefaults()
    override fun parseString(string: String): String = string
    override fun valueToString(value: String): String = value
    override fun getChoices(): List<String> = listOf(KotlinOfficialStyleGuide.CODE_STYLE_ID, KotlinObsoleteStyleGuide.CODE_STYLE_ID)
    override fun getPropertyName(): String = "code_style_defaults"
}

private fun applyKotlinCodeStyle(codeStyleId: String?, codeStyleSettings: CodeStyleSettings): Boolean {
    when (codeStyleId) {
        KotlinOfficialStyleGuide.CODE_STYLE_ID -> KotlinOfficialStyleGuide.apply(codeStyleSettings)
        KotlinObsoleteStyleGuide.CODE_STYLE_ID -> KotlinObsoleteStyleGuide.apply(codeStyleSettings)
        else -> return false
    }

    return true
}