// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * @author nik
 */
class ShowActionDescriptionsToggleAction : ToggleAction("Descriptions of Actions"), DumbAware {
    override fun isSelected(e: AnActionEvent) = getPresentationAssistant().configuration.showActionDescriptions

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        getPresentationAssistant().setShowActionsDescriptions(state, e.project)
    }
}