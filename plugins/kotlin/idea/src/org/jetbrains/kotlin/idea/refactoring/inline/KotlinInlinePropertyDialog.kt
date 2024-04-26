// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtWhenExpression

class KotlinInlinePropertyDialog(
    property: KtProperty,
    reference: PsiReference?,
    private val assignmentToDelete: KtBinaryExpression?,
    withPreview: Boolean = true,
    editor: Editor?,
) : AbstractKotlinInlinePropertyDialog(property, reference, withPreview, editor) {

    override fun createProcessor(): KotlinInlinePropertyProcessor = KotlinInlinePropertyProcessor(
        declaration = declaration,
        reference = reference,
        inlineThisOnly = isInlineThisOnly,
        deleteAfter = !isInlineThisOnly && !(isKeepTheDeclaration && reference != null),
        isWhenSubjectVariable = (declaration.parent as? KtWhenExpression)?.subjectVariable == declaration,
        editor = editor,
        statementToDelete = assignmentToDelete,
        project = project,
    )
}
