// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.codeInliner.TypeAliasUsageReplacementStrategy
import org.jetbrains.kotlin.idea.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.psi.KtTypeAlias

class KotlinInlineTypeAliasProcessor(
    declaration: KtTypeAlias,
    reference: PsiReference?,
    inlineThisOnly: Boolean,
    deleteAfter: Boolean,
    editor: Editor?,
    project: Project,
) : AbstractKotlinInlineNamedDeclarationProcessor<KtTypeAlias>(
    declaration = declaration,
    reference = reference,
    inlineThisOnly = inlineThisOnly,
    deleteAfter = deleteAfter,
    editor = editor,
    project = project,
) {
    override fun postAction() {
        performDelayedRefactoringRequests(myProject)
    }

    override fun createReplacementStrategy(): UsageReplacementStrategy = TypeAliasUsageReplacementStrategy(declaration)
}
