// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings

val CodeStyleSettings.kotlinCommonSettings: KotlinCommonCodeStyleSettings
    get() = getCommonSettings(KotlinLanguage.INSTANCE) as KotlinCommonCodeStyleSettings

val CodeStyleSettings.kotlinCustomSettings: KotlinCodeStyleSettings
    get() = getCustomSettings(KotlinCodeStyleSettings::class.java)

fun CodeStyleSettings.kotlinCodeStyleDefaults(): String? = kotlinCustomSettings.CODE_STYLE_DEFAULTS?.takeIf { customStyleId ->
    customStyleId == kotlinCommonSettings.CODE_STYLE_DEFAULTS
}

fun CodeStyleSettings.supposedKotlinCodeStyleDefaults(): String? =
    kotlinCustomSettings.CODE_STYLE_DEFAULTS ?: kotlinCommonSettings.CODE_STYLE_DEFAULTS

val PsiFile.kotlinCommonSettings: KotlinCommonCodeStyleSettings get() = CodeStyle.getSettings(this).kotlinCommonSettings
val PsiFile.kotlinCustomSettings: KotlinCodeStyleSettings get() = CodeStyle.getSettings(this).kotlinCustomSettings
val PsiFile.rightMarginOrDefault: Int get() = CodeStyle.getSettings(this).getRightMargin(KotlinLanguage.INSTANCE)