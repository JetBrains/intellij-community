// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement
import com.intellij.refactoring.actions.BasePlatformRefactoringAction
import com.intellij.refactoring.actions.ExtractSuperActionBase
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractIntroduceAction : BasePlatformRefactoringAction() {
    init {
        setInjectedContext(true)
    }

    final override fun setInjectedContext(worksInInjected: Boolean) {
        super.setInjectedContext(worksInInjected)
    }

    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean =
        elements.all { it is KtElement }

    override fun update(e: AnActionEvent) {
        super.update(e)
        ExtractSuperActionBase.removeFirstWordInMainMenu(this, e)
    }
}