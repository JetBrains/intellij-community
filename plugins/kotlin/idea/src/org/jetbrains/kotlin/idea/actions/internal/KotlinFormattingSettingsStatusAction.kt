// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.completion.KotlinIdeaCompletionBundle
import org.jetbrains.kotlin.idea.formatter.getKotlinFormatterKind

class KotlinFormattingSettingsStatusAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val formatterKind = getKotlinFormatterKind(project)

        Messages.showInfoMessage(
            project,
            KotlinIdeaCompletionBundle.message("formatting.settings.dialog.message.formatterkind", formatterKind),
            KotlinBundle.message("formatter.settings.title")
        )
    }
}
