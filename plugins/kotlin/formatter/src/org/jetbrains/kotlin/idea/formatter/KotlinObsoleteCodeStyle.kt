// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.kotlin.idea.KotlinLanguage

class KotlinObsoleteCodeStyle : KotlinPredefinedCodeStyle(KotlinObsoleteStyleGuide.CODE_STYLE_TITLE, KotlinLanguage.INSTANCE) {
    override val codeStyleId: String = KotlinObsoleteStyleGuide.CODE_STYLE_ID

    override fun apply(settings: CodeStyleSettings) {
        KotlinObsoleteStyleGuide.apply(settings)
    }

    companion object {
        val INSTANCE = KotlinObsoleteCodeStyle()
    }
}
