// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.KotlinPluginUtil
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode

class KotlinThrowExceptionAction : AnAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabledAndVisible = KotlinPluginUtil.isPatched() || isApplicationInternalMode()
    }

    override fun actionPerformed(e: AnActionEvent) {
        throw IllegalStateException("Kotlin Test Exception")
    }
}
