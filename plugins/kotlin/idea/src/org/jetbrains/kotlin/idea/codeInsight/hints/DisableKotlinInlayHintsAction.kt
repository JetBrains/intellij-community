// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiElement

class DisableKotlinInlayHintsAction(
    @NlsActions.ActionText hideDescription: String,
    private val hintType: HintType,
    private val project: Project,
    private val element: PsiElement
) : AnAction(hideDescription) {

    override fun actionPerformed(e: AnActionEvent) {
        toggleHintSetting(hintType, project, element) { false }
    }
}