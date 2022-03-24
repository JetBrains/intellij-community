// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

class RemoveAtFromAnnotationArgument(constructor: KtAnnotationEntry) : KotlinQuickFixAction<KtAnnotationEntry>(constructor) {

    override fun getText() = KotlinBundle.message("remove.from.annotation.argument")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val elementToReplace = (element?.parent as? KtAnnotatedExpression) ?: return

        val document = file.viewProvider.document
        val pos = elementToReplace.textRange.startOffset
        if (document.charsSequence[pos] == '@') {
            document.deleteString(pos, pos + 1)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtAnnotationEntry>? =
            (diagnostic.psiElement as? KtAnnotationEntry)?.let { RemoveAtFromAnnotationArgument(it) }
    }

}
