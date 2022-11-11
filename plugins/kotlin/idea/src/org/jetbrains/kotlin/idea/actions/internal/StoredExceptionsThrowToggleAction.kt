// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.utils.WrappedValues


class StoredExceptionsThrowToggleAction :
    ToggleAction(
        KotlinBundle.message("internal.toggle.throwing.cached.pce.title"),
        KotlinBundle.message("rethrow.stored.pce.as.a.new.runtime.exception"),
        null
    ) {
    override fun isSelected(e: AnActionEvent): Boolean {
        return WrappedValues.throwWrappedProcessCanceledException
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        WrappedValues.throwWrappedProcessCanceledException = state
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabled = ApplicationManager.getApplication().isInternal
    }
}
