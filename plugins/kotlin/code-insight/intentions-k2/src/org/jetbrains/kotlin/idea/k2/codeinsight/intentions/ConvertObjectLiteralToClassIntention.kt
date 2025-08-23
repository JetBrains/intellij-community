// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseStringExpression
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionData
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.createExtractionEngine
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

internal class ConvertObjectLiteralToClassIntention : SelfTargetingRangeIntention<KtObjectLiteralExpression>(
    KtObjectLiteralExpression::class.java,
    KotlinBundle.messagePointer("convert.object.literal.to.class")
) {
    override fun applicabilityRange(element: KtObjectLiteralExpression): TextRange? = element.objectDeclaration.getObjectKeyword()?.textRange

    override fun startInWriteAction(): Boolean = false

    private fun doApply(editor: Editor, element: KtObjectLiteralExpression, targetParent: KtElement) {
        val project = element.project
        val nameValidator = KotlinDeclarationNameValidator(
            element,
            true,
            KotlinNameSuggestionProvider.ValidatorTarget.CLASS
        )
        val containingClass = element.containingClass()
        val rangeMarker = editor.document.createRangeMarker(element.textRange)
        val holder = runWithModalProgressBlocking(
            project,
            KotlinBundle.message("progress.title.analyze.extraction.data")
        ) {
            val classNames = readAction {
                analyze(element) {
                    val validator: (String) -> Boolean = { nameValidator.validate(it) }

                    element.objectDeclaration.superTypeListEntries
                        .mapNotNull {
                            it.typeReference?.typeElement?.let { typeElement ->
                                KotlinNameSuggester.suggestTypeAliasNameByPsi(typeElement, validator)
                            }
                        }
                        .takeIf { it.isNotEmpty() } ?: listOf(KotlinNameSuggester.suggestNameByName("O", validator))
                }
            }

            readAction {
                val targetSibling = element.parentsWithSelf.first { it.parent == targetParent }
                val hasMemberReference = containingClass?.body?.allChildren?.any {
                    (it is KtProperty || it is KtNamedFunction) &&
                            ReferencesSearch.search(it, element.useScope).findFirst() != null
                } ?: false

                val data = ExtractionData(element.containingKtFile, element.toRange(), targetSibling)

                Holder(classNames, hasMemberReference, data)
            }
        }

        val className = holder.classNames.first()
        val psiFactory = KtPsiFactory(element.project)

        val objectDeclaration = element.objectDeclaration

        val newClass = psiFactory.createClass("class $className")
        objectDeclaration.getSuperTypeList()?.let {
            newClass.add(psiFactory.createColon())
            newClass.add(it)
        }

        objectDeclaration.body?.let {
            newClass.add(it)
        }

        val helper: ExtractionEngineHelper = object : ExtractionEngineHelper(text) {
            override fun configureAndRun(
                project: Project,
                editor: Editor,
                descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                onFinish: (ExtractionResult) -> Unit
            ) {
                project.executeCommand(text) {
                    val descriptor = runWithModalProgressBlocking(
                        project,
                        KotlinBundle.message("progress.title.analyze.extraction.data")
                    ) {
                        readAction {
                            descriptorWithConflicts.descriptor.copy(suggestedNames = listOf(className))
                        }
                    }
                    doRefactor(
                        ExtractionGeneratorConfiguration(descriptor, ExtractionGeneratorOptions.DEFAULT),
                        onFinish
                    )
                }
            }
        }

        createExtractionEngine(helper)
            .run(editor, holder.data) { extractionResult ->
                val functionDeclaration = extractionResult.declaration as KtFunction
                if (functionDeclaration.valueParameters.isNotEmpty()) {
                    val valKeyword = psiFactory.createValKeyword()
                    val whiteSpace = psiFactory.createWhiteSpace()
                    newClass.createPrimaryConstructorParameterListIfAbsent()
                        .replaced(functionDeclaration.valueParameterList!!)
                        .parameters
                        .forEach {
                            it.addAfter(whiteSpace, null)
                            it.addAfter(valKeyword, null)
                            it.addModifier(KtTokens.PRIVATE_KEYWORD)
                        }
                }

                val introducedClass = runWriteAction {
                    val replaced = functionDeclaration.replaced(newClass)
                    if (holder.hasMemberReference && containingClass == (replaced.parent.parent as? KtClass)) {
                        replaced.addModifier(KtTokens.INNER_KEYWORD)
                    }
                    replaced
                }

                PsiDocumentManager.getInstance(project).commitDocument(editor.document)

                val file = introducedClass.containingFile

                val templateScope = file
                // effectively inline template's text is the entire file,
                // range marker is created from the current offset + length of template's text
                // it has to be 0, otherwise it would be out of range error
                editor.caretModel.moveToOffset(templateScope.textRange.startOffset)

                val builder = TemplateBuilderImpl(templateScope)
                builder.replaceElement(introducedClass.nameIdentifier!!, NEW_CLASS_NAME, ChooseStringExpression(holder.classNames), true)

                runWriteAction {
                    file.findElementAt(rangeMarker.startOffset)?.findParentOfType<KtNameReferenceExpression>(false)
                        ?.let { referenceExpression ->
                            builder.replaceElement(referenceExpression, USAGE_VARIABLE_NAME, NEW_CLASS_NAME, false)
                        }

                    val template = builder.buildInlineTemplate()

                    TemplateManager.getInstance(project).startTemplate(editor, template)
                }
            }
    }

    private data class Holder(
        val classNames: List<String>,
        val hasMemberReference: Boolean,
        val data: ExtractionData,
    )

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