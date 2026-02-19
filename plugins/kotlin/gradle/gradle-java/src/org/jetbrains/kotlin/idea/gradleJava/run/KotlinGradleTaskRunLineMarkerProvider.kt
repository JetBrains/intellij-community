// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.lineMarker.ExecutorAction.Companion.getActions
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement

class KotlinGradleTaskRunLineMarkerProvider : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (!isInGradleKotlinScript(element)) return null
        if (!isRunTaskInGutterCandidate(element)) return null
        findTaskNameAround(element)?.takeIf { it.isNotEmpty() } ?: return null
        val actions = getActions()
        val event = createActionEvent(element)
        return Info(AllIcons.RunConfigurations.TestState.Run, actions) {
            actions.mapNotNull { action: AnAction -> getText(action, event) }
                .joinToString("\n")
        }
    }
}