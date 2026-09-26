// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile

/**
 * A [KotlinQuickFixAction] providing a similar API for quick fixes as
 * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention] provides for intentions.
 */
abstract class AbstractKotlinApplicableQuickFix<ELEMENT : PsiElement>(target: ELEMENT) : KotlinQuickFixAction<ELEMENT>(target) {
    open fun getActionName(element: ELEMENT): @IntentionName String = familyName

    abstract fun apply(element: ELEMENT, project: Project, editor: Editor?, file: KtFile)

    final override fun getText(): String {
        val element = element ?: return familyName
        return getActionName(element)
    }

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        apply(element, project, editor, file)
    }
}