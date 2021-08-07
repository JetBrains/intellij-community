// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ShowInlayHintsSettings : AnAction("Hints Settings...") {
    override fun actionPerformed(e: AnActionEvent) {
        val file = CommonDataKeys.PSI_FILE.getData(e.dataContext) ?: return
        val fileLanguage = file.language
        CompatibleInlayHintsConfigurable.showSettingsDialogForLanguage(
            file.project,
            fileLanguage
        )
    }
}