// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

internal class UndefinedIntentionAction(private val intentionFileName: String) : IntentionAction {
    override fun startInWriteAction(): Boolean {
        reportError()
    }

    override fun getFamilyName(): String {
        reportError()
    }

    override fun getText(): String {
        reportError()
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        reportError()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        reportError()
    }

    private fun reportError(): Nothing =
        error("""
            Intention action for this test is not defined.
            If it exists, please add its qualified name to the '$intentionFileName' file.
            Otherwise, add an ignore directive.
            """.trimIndent()
        )
}
