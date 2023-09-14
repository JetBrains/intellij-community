// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinIntroduceVariableServiceK1Impl(private val project: Project) : KotlinIntroduceVariableService {
    override fun findElementAtRange(
        file: KtFile,
        selectionStart: Int,
        selectionEnd: Int,
        elementKinds: Collection<ElementKind>
    ): PsiElement? = org.jetbrains.kotlin.idea.refactoring.findElementAtRange(
        file = file,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        elementKinds = elementKinds,
        failOnEmptySuggestion = false,
    )

    override fun getContainersForExpression(expression: KtExpression): List<Pair<KtElement, KtElement>> {
        return KotlinIntroduceVariableHandler.getContainersForExpression(expression)
    }

    override fun getSmartSelectSuggestions(
        file: PsiFile,
        offset: Int,
        elementKind: ElementKind
    ): List<KtElement> = org.jetbrains.kotlin.idea.refactoring.getSmartSelectSuggestions(file, offset, elementKind)

    override fun findOccurrences(
        expression: KtExpression,
        occurrenceContainer: PsiElement
    ): List<KtExpression> = with(KotlinIntroduceVariableHandler) {
        expression.findOccurrences(occurrenceContainer)
    }

    override fun doRefactoringWithContainer(
        editor: Editor?,
        expressionToExtract: KtExpression,
        container: KtElement,
        occurrencesToReplace: List<KtExpression>?
    ) {
        KotlinIntroduceVariableHandler.doRefactoringWithContainer(
            project = project,
            editor = editor,
            expressionToExtract = expressionToExtract,
            container = container,
            isVar = false,
            occurrencesToReplace = occurrencesToReplace,
            onNonInteractiveFinish = null
        )
    }
}