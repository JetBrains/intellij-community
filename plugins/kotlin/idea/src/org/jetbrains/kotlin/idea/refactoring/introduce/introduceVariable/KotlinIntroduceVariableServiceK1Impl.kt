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
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.refactoring.introduce.findStringTemplateFragment
import org.jetbrains.kotlin.idea.refactoring.introduce.findStringTemplateOrStringTemplateEntryExpression
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
            ?: findStringTemplateOrStringTemplateEntryExpression(file, startOffset, endOffset, elementKind)
            ?: findStringTemplateFragment(file, startOffset, endOffset, elementKind)

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

    override fun getContainersForExpression(expression: KtExpression): List<KotlinIntroduceVariableHelper.Containers> =
        with(K1IntroduceVariableHandler) { expression.getCandidateContainers() }

    override fun findOccurrences(
        expression: KtExpression,
        occurrenceContainer: PsiElement
    ): List<KtExpression> = with(K1IntroduceVariableHandler) {
        expression.findOccurrences(occurrenceContainer)
    }

    override fun doRefactoringWithContainer(
        editor: Editor?,
        expressionToExtract: KtExpression,
        container: KtElement,
        occurrencesToReplace: List<KtExpression>?
    ) {
        K1IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
            project = project,
            editor = editor,
            expressionToExtract = expressionToExtract,
            isVar = false,
            occurrencesToReplace = occurrencesToReplace,
            onNonInteractiveFinish = null,
            targetContainer = container,
        )
    }

    override fun hasUnitType(element: KtExpression): Boolean {
        val bindingContext = element.analyze(BodyResolveMode.FULL)
        val expressionType = bindingContext.getType(element)
        return (expressionType == null || KotlinBuiltIns.isUnit(expressionType))
    }
}