// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.test.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.test.SettingsConfigurator

object FormatSettingsUtil {
    fun createConfigurator(fileText: String, settings: CodeStyleSettings): SettingsConfigurator {
        val commonSettings = settings.getCommonSettings(KotlinLanguage.INSTANCE)
        return SettingsConfigurator(
            fileText,
            settings.getCustomSettings(KotlinCodeStyleSettings::class.java),
            commonSettings,
            commonSettings.indentOptions,
        )
    }
}
