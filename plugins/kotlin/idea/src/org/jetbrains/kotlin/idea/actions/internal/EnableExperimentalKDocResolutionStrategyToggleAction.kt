/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode

internal class EnableExperimentalKDocResolutionStrategyToggleAction: ToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean {
        return Registry.`is`("kotlin.analysis.experimentalKDocResolution", false)
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        Registry.get("kotlin.analysis.experimentalKDocResolution").setValue(state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabled = isApplicationInternalMode()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}