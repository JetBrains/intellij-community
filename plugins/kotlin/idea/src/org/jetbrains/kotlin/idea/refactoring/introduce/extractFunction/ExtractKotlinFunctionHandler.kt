// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplate
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.core.util.range
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ui.KotlinExtractFunctionDialog
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.nonBlocking
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ExtractKotlinFunctionHandler(
    private val allContainersEnabled: Boolean = false,
    private val helper: ExtractionEngineHelper = InteractiveExtractionHelper
) : RefactoringActionHandler {

    companion object {
        private val isInplaceRefactoringEnabled: Boolean
            get() {
                return EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
                       && Registry.`is`("kotlin.enable.inplace.extract.method")
            }
    }

    object InteractiveExtractionHelper : ExtractionEngineHelper(EXTRACT_FUNCTION) {
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            if (isInplaceRefactoringEnabled) {
                val descriptor = descriptorWithConflicts.descriptor.copy(suggestedNames = listOf("extracted"))
                val configuration = ExtractionGeneratorConfiguration(descriptor, ExtractionGeneratorOptions.DEFAULT)
                doRefactor(configuration, onFinish)
            } else {
                KotlinExtractFunctionDialog(descriptorWithConflicts.descriptor.extractionData.project, descriptorWithConflicts) {
                    doRefactor(it.currentConfiguration, onFinish)
                }.show()
            }
        }
    }

    fun doInvoke(
        editor: Editor,
        file: KtFile,
        elements: List<PsiElement>,
        targetSibling: PsiElement
    ) {
        nonBlocking(file.project, {
            val adjustedElements = elements.singleOrNull().safeAs<KtBlockExpression>()?.statements ?: elements
            ExtractionData(file, adjustedElements.toRange(false), targetSibling)
        }) { extractionData ->
            val callRange = editor.document.createRangeMarker(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
                .apply { isGreedyToLeft = true; isGreedyToRight = true }
            val editorState = EditorState(editor)
            ExtractionEngine(helper).run(editor, extractionData) { extraction ->
                if (isInplaceRefactoringEnabled) {
                    val callIdentifier = findSingleCallExpression(file, callRange.range)?.calleeExpression ?: throw IllegalStateException()
                    val methodRange = extraction.declaration.textRange
                    val methodOffset = extraction.declaration.navigationElement.textRange.endOffset
                    val callOffset = callIdentifier.textRange.endOffset
                    val preview = InplaceExtractUtils.createPreview(editor, methodRange, methodOffset, callRange.range!!, callOffset)
                    val templateState = ExtractMethodTemplate(editor, extraction.declaration, callIdentifier).runTemplate(LinkedHashSet())
                    templateState.properties.put("ExtractMethod", true)
                    callRange.dispose()
                    Disposer.register(templateState, preview)
                    templateState.addTemplateStateListener(object: TemplateEditingAdapter() {
                        override fun templateFinished(template: Template, brokenOff: Boolean) {
                            if (brokenOff) {
                                editorState.revert()
                            }
                        }
                    })
                    InplaceExtractUtils.addTemplateFinishedListener(templateState) {
                        processDuplicates(extraction.duplicateReplacers, file.project, editor)
                    }
                } else {
                    processDuplicates(extraction.duplicateReplacers, file.project, editor)
                }
            }
        }
    }

    private fun findSingleCallExpression(file: KtFile, range: TextRange?): KtCallExpression? {
        if (range == null) return null
        val container = PsiTreeUtil.findCommonParent(file.findElementAt(range.startOffset), file.findElementAt(range.endOffset))
        val callExpressions = PsiTreeUtil.findChildrenOfType(container, KtCallExpression::class.java)
        return callExpressions.filter { it.textRange in range }.singleOrNull()
    }

    fun selectElements(editor: Editor, file: KtFile, continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit) {
        selectElementsWithTargetSibling(
            EXTRACT_FUNCTION,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(CodeInsightUtils.ElementKind.EXPRESSION),
            ::validateExpressionElements,
            { elements, parent -> parent.getExtractionContainers(elements.size == 1, allContainersEnabled) },
            continuation
        )
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElements(editor, file) { elements, targetSibling -> doInvoke(editor, file, elements, targetSibling) }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("Extract Function can only be invoked from editor")
    }
}

val EXTRACT_FUNCTION: String
    @Nls
    get() = KotlinBundle.message("extract.function")
