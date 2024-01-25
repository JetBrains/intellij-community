// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringHelper
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.codeInsight.shorten.prepareDelayedRequests

class KotlinRefactoringHelperForDelayedRequests : RefactoringHelper<Any> {
    override fun prepareOperation(
        usages: Array<out UsageInfo>,
        elements: List<PsiElement>
    ): Any? {
        if (usages.isNotEmpty()) {
            val project = usages[0].project
            prepareDelayedRequests(project)
        }
        return null
    }

    override fun performOperation(project: Project, operationData: Any?) {
        runWriteAction { performDelayedRefactoringRequests(project) }
    }
}
