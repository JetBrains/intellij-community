// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlin.idea.configuration.KotlinLanguageConfiguration

class ConfigurePluginUpdatesAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        ShowSettingsUtilImpl.showSettingsDialog(project, KotlinLanguageConfiguration.ID, "")
    }

    companion object {
        val ACTION_ID = "KotlinConfigureUpdates"
    }
}
