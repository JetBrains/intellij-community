// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

object ProjectCodeStyleImporter {
    fun apply(project: Project, codeStyleStr: String?): Boolean = when (codeStyleStr) {
        KotlinObsoleteCodeStyle.CODE_STYLE_SETTING -> {
            apply(project, KotlinObsoleteCodeStyle.INSTANCE)
            true
        }

        KotlinStyleGuideCodeStyle.CODE_STYLE_SETTING -> {
            apply(project, KotlinStyleGuideCodeStyle.INSTANCE)
            true
        }

        else -> false
    }

    fun apply(project: Project, predefinedCodeStyle: KotlinPredefinedCodeStyle) {
        val customSettings = CodeStyle.getSettings(project)
        if (predefinedCodeStyle.codeStyleId == customSettings.kotlinCodeStyleDefaults()) {
            // Don't bother user that already have correct code style
            return
        }

        val settingsManager = CodeStyleSettingsManager.getInstance(project)
        val projectSettingsUpdated: CodeStyleSettings = if (CodeStyle.usesOwnSettings(project)) {
            settingsManager.cloneSettings(customSettings)
        } else {
            CodeStyle.getDefaultSettings()
        }

        predefinedCodeStyle.apply(projectSettingsUpdated)
        CodeStyle.setMainProjectSettings(project, projectSettingsUpdated)
        settingsManager.notifyCodeStyleSettingsChanged()
    }
}