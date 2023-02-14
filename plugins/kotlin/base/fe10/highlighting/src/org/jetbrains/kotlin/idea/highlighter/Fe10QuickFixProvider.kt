// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtElement

interface Fe10QuickFixProvider {
    companion object {
        fun getInstance(project: Project): Fe10QuickFixProvider = project.service()
    }

    fun createQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction>

    fun createUnresolvedReferenceQuickFixes(sameTypeDiagnostics: Collection<Diagnostic>): MultiMap<Diagnostic, IntentionAction>

    fun createSuppressFix(element: KtElement, suppressionKey: String, hostKind: AnnotationHostKind): SuppressIntentionAction
}

class AnnotationHostKind(
    /** Human-readable `KtElement` kind on which the annotation is placed. E.g., 'file', 'class' or 'statement'. */
    @Nls val kind: String,

    /** Name of the annotation owner. Might be null if the owner is not a named declaration (for instance, if it is a statement). */
    @NlsSafe val name: String?,

    /** True if the annotation needs to be added to a separate line. */
    val newLineNeeded: Boolean
)

object RegisterQuickFixesLaterIntentionAction : IntentionAction {
    override fun getText(): String = ""

    override fun getFamilyName(): String = ""

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = Unit

    override fun startInWriteAction(): Boolean = false
}