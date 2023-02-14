// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.storage.CacheResetOnProcessCanceled

class CacheResetOnProcessCanceledToggleAction : ToggleAction() {
    override fun isSelected(e: AnActionEvent): Boolean =
        CacheResetOnProcessCanceled.enabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        CacheResetOnProcessCanceled.enabled = state
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = isApplicationInternalMode()
    }
}
