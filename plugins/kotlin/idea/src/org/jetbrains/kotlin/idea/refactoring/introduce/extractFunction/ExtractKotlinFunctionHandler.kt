// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ui.KotlinExtractFunctionDialog
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.nonBlocking
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ExtractKotlinFunctionHandler(
    private val allContainersEnabled: Boolean = false,
    private val helper: ExtractionEngineHelper = getDefaultHelper(allContainersEnabled)
) : RefactoringActionHandler {

    companion object {
        private val isInplaceRefactoringEnabled: Boolean
            get() {
                return EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
                       && Registry.`is`("kotlin.enable.inplace.extract.method")
            }

        fun getDefaultHelper(allContainersEnabled: Boolean): ExtractionEngineHelper {
            return if (isInplaceRefactoringEnabled) InplaceExtractionHelper(allContainersEnabled) else InteractiveExtractionHelper
        }
    }

    object InteractiveExtractionHelper : ExtractionEngineHelper(EXTRACT_FUNCTION) {
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            fun afterFinish(extraction: ExtractionResult){
                processDuplicates(extraction.duplicateReplacers, project, editor)
                onFinish(extraction)
            }
            KotlinExtractFunctionDialog(descriptorWithConflicts.descriptor.extractionData.project, descriptorWithConflicts) {
                doRefactor(it.currentConfiguration, ::afterFinish)
            }.show()
        }
    }

    open class InplaceExtractionHelper(private val allContainersEnabled: Boolean) : ExtractionEngineHelper(EXTRACT_FUNCTION) {
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            val activeTemplateState = TemplateManagerImpl.getTemplateState(editor)
            if (activeTemplateState != null) {
                activeTemplateState.gotoEnd(true)
                ExtractKotlinFunctionHandler(allContainersEnabled, InteractiveExtractionHelper)
                    .invoke(project, editor, descriptorWithConflicts.descriptor.extractionData.originalFile, null)
            }
            val suggestedNames = descriptorWithConflicts.descriptor.suggestedNames.takeIf { it.isNotEmpty() } ?: listOf("extracted")
            val descriptor = descriptorWithConflicts.descriptor.copy(suggestedNames = suggestedNames)
            val elements = descriptor.extractionData.originalElements
            val file = descriptor.extractionData.originalFile
            val callTextRange = TextRange(rangeOf(elements.first()).startOffset, rangeOf(elements.last()).endOffset)

            val commonParent = descriptor.extractionData.commonParent
            val container = commonParent.takeIf { commonParent != elements.firstOrNull() } ?: commonParent.parent
            val callRangeProvider: () -> TextRange? = createSmartRangeProvider(container, callTextRange)
            val editorState = EditorState(project, editor)
            val disposable = Disposer.newDisposable()
            WriteCommandAction.writeCommandAction(project).run<Throwable> {
                val startMarkAction = StartMarkAction.start(editor, project, EXTRACT_FUNCTION)
                Disposer.register(disposable) { FinishMarkAction.finish(project, editor, startMarkAction) }
            }
            fun afterFinish(extraction: ExtractionResult){
                val callRange: TextRange = callRangeProvider.invoke() ?: throw IllegalStateException()
                val callIdentifier = findSingleCallExpression(file, callRange)?.calleeExpression ?: throw IllegalStateException()
                val methodIdentifier = extraction.declaration.nameIdentifier ?: throw IllegalStateException()
                val methodRange = extraction.declaration.textRange
                val methodOffset = extraction.declaration.navigationElement.textRange.endOffset
                val callOffset = callIdentifier.textRange.endOffset
                val preview = InplaceExtractUtils.createPreview(editor, methodRange, methodOffset, callRange, callOffset)
                Disposer.register(disposable, preview)
                val templateField = TemplateField(callIdentifier.textRange, listOf(methodIdentifier.textRange))
                    .withCompletionNames(descriptor.suggestedNames)
                    .withCompletionHint(getDialogAdvertisement())
                    .withValidation { variableRange ->
                        val error = getIdentifierError(file, variableRange)
                        if (error != null) {
                            InplaceExtractUtils.showErrorHint(editor, variableRange.endOffset, error)
                        }
                        error == null
                    }
                ExtractMethodTemplateBuilder(editor, EXTRACT_FUNCTION)
                    .enableRestartForHandler(ExtractKotlinFunctionHandler::class.java)
                    .onBroken {
                        editorState.revert()
                    }
                    .onSuccess {
                        processDuplicates(extraction.duplicateReplacers, file.project, editor)
                    }
                    .disposeWithTemplate(disposable)
                    .createTemplate(file, listOf(templateField))
                onFinish(extraction)
            }
            try {
                val configuration = ExtractionGeneratorConfiguration(descriptor, ExtractionGeneratorOptions.DEFAULT)
                doRefactor(configuration, ::afterFinish)
            } catch (e: Throwable) {
                Disposer.dispose(disposable)
                throw e
            }
        }

        @Nls
        private fun getDialogAdvertisement():  String {
            val shortcut = KeymapUtil.getPrimaryShortcut("ExtractFunction") ?: throw IllegalStateException("Action is not found")
            return RefactoringBundle.message("inplace.refactoring.advertisement.text", KeymapUtil.getShortcutText(shortcut))
        }

        protected fun rangeOf(element: PsiElement): TextRange {
            return (element as? KtExpression)?.extractableSubstringInfo?.contentRange ?: element.textRange
        }

        protected fun createSmartRangeProvider(container: PsiElement, range: TextRange): () -> TextRange? {
            val offsetFromStart = range.startOffset - container.textRange.startOffset
            val offsetFromEnd = container.textRange.endOffset - range.endOffset
            val pointer = SmartPointerManager.createPointer(container)
            fun findRange(): TextRange? {
                val containerRange = pointer.range ?: return null
                return TextRange(containerRange.startOffset + offsetFromStart, containerRange.endOffset - offsetFromEnd)
            }
            return ::findRange
        }

        @Nls
        private fun getIdentifierError(file: PsiFile, variableRange: TextRange): String? {
            val call = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, KtCallExpression::class.java, false)
            val name = file.viewProvider.document.getText(variableRange)
            return if (! KotlinNamesValidator().isIdentifier(name, file.project)) {
                JavaRefactoringBundle.message("template.error.invalid.identifier.name")
            } else if (call?.resolveToCall() == null) {
                JavaRefactoringBundle.message("extract.method.error.method.conflict")
            } else {
                null
            }
        }

        protected fun findSingleCallExpression(file: KtFile, range: TextRange?): KtCallExpression? {
            if (range == null) return null
            val container = PsiTreeUtil.findCommonParent(file.findElementAt(range.startOffset), file.findElementAt(range.endOffset))
            val callExpressions = PsiTreeUtil.findChildrenOfType(container, KtCallExpression::class.java)
            return callExpressions.singleOrNull { it.textRange in range }
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
            ExtractionEngine(helper).run(editor, extractionData)
        }
    }

    fun selectElements(editor: Editor, file: KtFile, continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit) {
        selectElementsWithTargetSibling(
            EXTRACT_FUNCTION,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(ElementKind.EXPRESSION),
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
