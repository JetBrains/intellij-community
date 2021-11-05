// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty.KotlinInplacePropertyIntroducer
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHintByKey
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.psi.patternMatching.toRange
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinIntroduceConstantHandler(
    val helper: ExtractionEngineHelper = InteractiveExtractionHelper
) : RefactoringActionHandler {
    object InteractiveExtractionHelper : ExtractionEngineHelper(INTRODUCE_CONSTANT) {
        private fun getExtractionTarget(descriptor: ExtractableCodeDescriptor) =
            propertyTargets.firstOrNull { it.isAvailable(descriptor) }

        override fun validate(descriptor: ExtractableCodeDescriptor) =
            descriptor.validate(getExtractionTarget(descriptor) ?: ExtractionTarget.FUNCTION)

        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            val descriptor = descriptorWithConflicts.descriptor
            val target = getExtractionTarget(descriptor)
            if (target != null) {
                val options = ExtractionGeneratorOptions(target = target, delayInitialOccurrenceReplacement = true, isConst = true)
                doRefactor(ExtractionGeneratorConfiguration(descriptor, options), onFinish)
            } else {
                showErrorHint(
                    project,
                    editor,
                    KotlinBundle.message("error.text.can.t.introduce.constant.for.this.expression"),
                    INTRODUCE_CONSTANT
                )
            }
        }
    }

    fun doInvoke(project: Project, editor: Editor, file: KtFile, elements: List<PsiElement>, target: PsiElement) {
        val adjustedElements = (elements.singleOrNull() as? KtBlockExpression)?.statements ?: elements
        when {
            adjustedElements.isEmpty() -> {
                showErrorHintByKey(
                    project, editor, "cannot.refactor.no.expression",
                    INTRODUCE_CONSTANT
                )
            }
            else -> {
                val options = ExtractionOptions(extractAsProperty = true)
                val extractionData = ExtractionData(file, adjustedElements.toRange(), target, null, options)
                ExtractionEngine(helper).run(editor, extractionData) {
                    val property = it.declaration as KtProperty
                    val descriptor = it.config.descriptor

                    editor.caretModel.moveToOffset(property.textOffset)
                    editor.selectionModel.removeSelection()
                    if (editor.settings.isVariableInplaceRenameEnabled && !isUnitTestMode()) {
                        with(PsiDocumentManager.getInstance(project)) {
                            commitDocument(editor.document)
                            doPostponedOperationsAndUnblockDocument(editor.document)
                        }

                        val introducer = KotlinInplacePropertyIntroducer(
                            property = property,
                            editor = editor,
                            project = project,
                            title = INTRODUCE_CONSTANT,
                            doNotChangeVar = false,
                            exprType = descriptor.returnType,
                            extractionResult = it,
                            availableTargets = listOf(ExtractionTarget.PROPERTY_WITH_GETTER)
                        )
                        introducer.performInplaceRefactoring(LinkedHashSet(getNameSuggestions(property) + descriptor.suggestedNames))
                    } else {
                        processDuplicatesSilently(it.duplicateReplacers, project)
                    }
                }
            }
        }
    }

    private fun getNameSuggestions(property: KtProperty): List<String> {
        val initializerValue = property.initializer.safeAs<KtStringTemplateExpression>()?.plainContent
        val identifierValue = property.identifyingElement?.text

        return listOfNotNull(initializerValue, identifierValue).map { NameUtil.capitalizeAndUnderscore(it) }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElements(editor, file) { elements, targets -> doInvoke(project, editor, file, elements, targets) }
    }

    fun selectElements(
        editor: Editor,
        file: KtFile,
        continuation: (elements: List<PsiElement>, targets: PsiElement) -> Unit
    ) {

        selectElementsWithTargetSibling(
            INTRODUCE_CONSTANT,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(CodeInsightUtils.ElementKind.EXPRESSION),
            ::validateElements,
            { _, sibling ->
                sibling.getExtractionContainers(strict = true, includeAll = true)
                    .filter { (it is KtFile && !it.isScript()) }
            },
            continuation
        )
    }

    private fun validateElements(elements: List<PsiElement>): String? {
        val errorMessage = validateExpressionElements(elements)
        return when {
            errorMessage != null -> errorMessage
            elements.any {
                // unchecked cast always succeeds because only expressions are selected in selectElements
                (it as KtExpression).isNotConst()
            } -> KotlinBundle.message(
                "error.text.can.t.introduce.constant.for.this.expression.because.not.constant"
            )
            else -> null
        }
    }

    private fun KtExpression.isNotConst(): Boolean {
        when (this) {
            // Handle these two expressions separately because in case of selecting part of a string
            // a temp file will be created in which the analysis fails
            is KtConstantExpression -> return false
            is KtStringTemplateExpression -> return this.hasInterpolation()
            else -> {
                val constInfo = ConstantExpressionEvaluator.getConstant(this, analyze(BodyResolveMode.PARTIAL))
                return constInfo == null || constInfo.usesNonConstValAsConstant
            }
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$INTRODUCE_CONSTANT can only be invoked from editor")
    }
}

val INTRODUCE_CONSTANT: String
    @Nls
    get() = KotlinBundle.message("introduce.constant")
