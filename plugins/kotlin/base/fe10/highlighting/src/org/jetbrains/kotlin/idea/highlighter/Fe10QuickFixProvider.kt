// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.inspections.suppress.AnnotationHostKind
import org.jetbrains.kotlin.psi.KtElement

interface Fe10QuickFixProvider {
    companion object {
        fun getInstance(project: Project): Fe10QuickFixProvider = project.service()
    }

    fun createQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction>

    fun createPostponedUnresolvedReferencesQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction>

    fun createUnresolvedReferenceQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction>

    fun createSuppressFix(element: KtElement, suppressionKey: String, hostKind: AnnotationHostKind): SuppressIntentionAction
}

object RegisterQuickFixesLaterIntentionAction : IntentionAction {
    override fun getText(): String = ""

    override fun getFamilyName(): String = ""

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = Unit

    override fun startInWriteAction(): Boolean = false
}