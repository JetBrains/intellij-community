// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile


@Deprecated(
    "Internal Kotlin plugin API",
    ReplaceWith("IntentionAction", "com.intellij.codeInsight.intention.IntentionAction")
)
abstract class KotlinQuickFixAction<out T : PsiElement>(element: T) : org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction<T>(element) {
    abstract override fun invoke(project: Project, editor: Editor?, file: KtFile)
}