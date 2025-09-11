// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.completion.command.commands.IntentionCommandSkipper
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.psi.PsiFile

class KotlinIntentionCommandSkipper : IntentionCommandSkipper {
    override fun skip(action: CommonIntentionAction, psiFile: PsiFile, offset: Int): Boolean {
        if (action !is IntentionAction) return false
        val unwrappedIntentionAction = IntentionActionDelegate.unwrap(action)
        return unwrappedIntentionAction is IntroduceVariableIntention
    }
}