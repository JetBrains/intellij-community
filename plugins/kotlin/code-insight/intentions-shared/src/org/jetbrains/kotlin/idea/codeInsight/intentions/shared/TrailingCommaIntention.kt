// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.editor.Editor
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.formatter.trailingComma.canAddTrailingCommaWithRegistryCheck
import org.jetbrains.kotlin.psi.KtElement

internal class TrailingCommaIntention : SelfTargetingIntention<KtElement>(
    KtElement::class.java,
    KotlinBundle.messagePointer("intention.trailing.comma.text")
), LowPriorityAction {
    override fun applyTo(element: KtElement, editor: Editor?) {
        SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.SETTINGS)
        val kotlinCustomSettings = element.containingKtFile.kotlinCustomSettings
        kotlinCustomSettings.ALLOW_TRAILING_COMMA = !kotlinCustomSettings.ALLOW_TRAILING_COMMA
        CodeStyleSettingsManager.getInstance(element.project).notifyCodeStyleSettingsChanged()
    }

    override fun isApplicableTo(element: KtElement, caretOffset: Int): Boolean = element.canAddTrailingCommaWithRegistryCheck().also {
        val actionNumber = 1.takeIf { element.containingKtFile.kotlinCustomSettings.ALLOW_TRAILING_COMMA } ?: 0
        setTextGetter(KotlinBundle.messagePointer("intention.trailing.comma.custom.text", actionNumber))
    }

    override fun startInWriteAction(): Boolean = false
}
