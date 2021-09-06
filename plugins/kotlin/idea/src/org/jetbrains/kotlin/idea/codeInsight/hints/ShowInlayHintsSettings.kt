// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.kotlin.idea.KotlinBundle

class ShowInlayHintsSettings : AnAction(KotlinBundle.message("action.hints.settings.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val fileLanguage = file.language
        InlayHintsConfigurable.showSettingsDialogForLanguage(
            file.project,
            fileLanguage
        )
    }
}