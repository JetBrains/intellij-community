// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseStringExpression
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier

class ConvertObjectLiteralToClassIntention : SelfTargetingRangeIntention<KtObjectLiteralExpression>(
    KtObjectLiteralExpression::class.java,
    KotlinBundle.messagePointer("convert.object.literal.to.class")
) {
    override fun applicabilityRange(element: KtObjectLiteralExpression): TextRange? = element.objectDeclaration.getObjectKeyword()?.textRange

    override fun startInWriteAction(): Boolean = false

    private fun doApply(editor: Editor, element: KtObjectLiteralExpression, targetParent: KtElement) {
        val project = element.project

        val scope = element.getResolutionScope()

        val validator: (String) -> Boolean = { scope.findClassifier(Name.identifier(it), NoLookupLocation.FROM_IDE) == null }
        val classNames = element.objectDeclaration.superTypeListEntries
            .mapNotNull {
                it.typeReference?.typeElement?.let { typeElement ->
                KotlinNameSuggester.suggestTypeAliasNameByPsi(typeElement, validator) }
            }
            .takeIf { it.isNotEmpty() } ?: listOf(KotlinNameSuggester.suggestNameByName("O", validator))

        val className = classNames.first()
        val psiFactory = KtPsiFactory(element.project)

        val targetSibling = element.parentsWithSelf.first { it.parent == targetParent }

        val objectDeclaration = element.objectDeclaration

        val containingClass = element.containingClass()
        val hasMemberReference = containingClass?.body?.allChildren?.any {
            (it is KtProperty || it is KtNamedFunction) &&
                    ReferencesSearch.search(it, element.useScope).findFirst() != null
        } ?: false

        val newClass = psiFactory.createClass("class $className")
        objectDeclaration.getSuperTypeList()?.let {
            newClass.add(psiFactory.createColon())
            newClass.add(it)
        }

        objectDeclaration.body?.let {
            newClass.add(it)
        }

        ExtractionEngine(
            object : ExtractionEngineHelper(text) {
                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    project.executeCommand(text) {
                        val descriptor = descriptorWithConflicts.descriptor.copy(suggestedNames = listOf(className))
                        doRefactor(
                            ExtractionGeneratorConfiguration(descriptor, ExtractionGeneratorOptions.DEFAULT),
                            onFinish
                        )
                    }
                }
            }
        ).run(editor, ExtractionData(element.containingKtFile, element.toRange(), targetSibling)) { extractionResult ->
            val functionDeclaration = extractionResult.declaration as KtFunction
            if (functionDeclaration.valueParameters.isNotEmpty()) {
                val valKeyword = psiFactory.createValKeyword()
                newClass.createPrimaryConstructorParameterListIfAbsent()
                    .replaced(functionDeclaration.valueParameterList!!)
                    .parameters
                    .forEach {
                        it.addAfter(valKeyword, null)
                        it.addModifier(KtTokens.PRIVATE_KEYWORD)
                    }
            }

            val introducedClass = runWriteAction {
                functionDeclaration.replaced(newClass).apply {
                    if (hasMemberReference && containingClass == (parent.parent as? KtClass)) addModifier(KtTokens.INNER_KEYWORD)
                    primaryConstructor?.reformatted()
                }.let { CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(it) }
            } ?: return@run

            val file = introducedClass.containingFile

            val template = TemplateBuilderImpl(file).let { builder ->
                builder.replaceElement(introducedClass.nameIdentifier!!, NEW_CLASS_NAME, ChooseStringExpression(classNames), true)
                for (psiReference in ReferencesSearch.search(introducedClass, LocalSearchScope(file), false).asIterable()) {
                    runWriteAction {
                        builder.replaceElement(psiReference.element, USAGE_VARIABLE_NAME, NEW_CLASS_NAME, false)
                    }
                }
                runWriteAction {
                    builder.buildInlineTemplate()
                }
            }

            editor.caretModel.moveToOffset(file.startOffset)
            runWriteAction {
                TemplateManager.getInstance(project).startTemplate(editor, template)
            }
        }
    }

    override fun applyTo(element: KtObjectLiteralExpression, editor: Editor?) {
        if (editor == null) return

        val containers = element.getExtractionContainers(strict = true, includeAll = true)

        if (isUnitTestMode()) {
            val targetComment = element.containingKtFile.findDescendantOfType<PsiComment>()?.takeIf {
                it.text == "// TARGET_BLOCK:"
            }

            val target = containers.firstOrNull { it == targetComment?.parent } ?: containers.last()
            return doApply(editor, element, target)
        }

        chooseContainerElementIfNecessary(
            containers,
            editor,
            if (containers.first() is KtFile) KotlinBundle.message("select.target.file") else KotlinBundle.message("select.target.code.block.file"),
            true
        ) {
            doApply(editor, element, it)
        }
    }
}

private const val NEW_CLASS_NAME = "NEW_CLASS_NAME"

private const val USAGE_VARIABLE_NAME = "OTHER_VAR"
