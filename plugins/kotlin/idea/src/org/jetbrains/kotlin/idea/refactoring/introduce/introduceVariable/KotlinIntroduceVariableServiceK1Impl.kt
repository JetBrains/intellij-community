// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.introduce.IntroduceRefactoringException
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.refactoring.introduce.findExpressionOrStringFragment
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.findElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ClassQualifier

internal class KotlinIntroduceVariableServiceK1Impl(private val project: Project) : KotlinIntroduceVariableService {
    override fun findElement(
        file: KtFile,
        startOffset: Int,
        endOffset: Int,
        failOnNoExpression: Boolean,
        elementKind: ElementKind
    ): PsiElement? {
        var element = findElement(file, startOffset, endOffset, elementKind)
        if (element == null && elementKind == ElementKind.EXPRESSION) {
            element = findExpressionOrStringFragment(file, startOffset, endOffset)
        }

        if (element is KtExpression) {
            val qualifier = element.analyze().get(BindingContext.QUALIFIER, element)
            if (qualifier != null && (qualifier !is ClassQualifier || qualifier.descriptor.kind != ClassKind.OBJECT)) {
                element = null
            }
        }

        if (element == null) {
            //todo: if it's infix expression => add (), then commit document then return new created expression

            if (failOnNoExpression) {
                throw IntroduceRefactoringException(KotlinBundle.message("cannot.refactor.not.expression"))
            }
            return null
        }

        return element
    }

    override fun getContainersForExpression(expression: KtExpression): List<Pair<KtElement, KtElement>> {
        return KotlinIntroduceVariableHandler.getContainersForExpression(expression)
    }

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

    override fun hasUnitType(element: KtExpression): Boolean {
        val bindingContext = element.analyze(BodyResolveMode.FULL)
        val expressionType = bindingContext.getType(element)
        return (expressionType == null || KotlinBuiltIns.isUnit(expressionType))
    }
}