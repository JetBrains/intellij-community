// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ui.KotlinExtractFunctionDialog
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.util.nonBlocking
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ExtractKotlinFunctionHandler(
    val allContainersEnabled: Boolean = false,
    private val helper: ExtractionEngineHelper = getDefaultHelper(allContainersEnabled)
) : AbstractExtractKotlinFunctionHandler(allContainersEnabled) {

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
            fun afterFinish(extraction: ExtractionResult) {
                processDuplicates(extraction.duplicateReplacers, project, editor)
                onFinish(extraction)
            }
            KotlinExtractFunctionDialog(descriptorWithConflicts.descriptor.extractionData.project, descriptorWithConflicts) {
                doRefactor(it.currentConfiguration, ::afterFinish)
            }.show()
        }
    }

    open class InplaceExtractionHelper(
        val allContainersEnabled: Boolean
    ) : ExtractionEngineHelper(EXTRACT_FUNCTION),
        AbstractInplaceExtractionHelper<KotlinType, ExtractionResult, ExtractableCodeDescriptorWithConflicts> {
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            super<AbstractInplaceExtractionHelper>.configureAndRun(project, editor, descriptorWithConflicts, onFinish)
        }

        override fun createRestartHandler(): AbstractExtractKotlinFunctionHandler = ExtractKotlinFunctionHandler(
            allContainersEnabled, InteractiveExtractionHelper
        )

        override fun createInplaceRestartHandler(): AbstractExtractKotlinFunctionHandler =
            ExtractKotlinFunctionHandler(allContainersEnabled, this)

        override fun doRefactor(descriptor: IExtractableCodeDescriptor<KotlinType>, onFinish: (ExtractionResult) -> Unit) {
            val configuration = ExtractionGeneratorConfiguration(descriptor as ExtractableCodeDescriptor, ExtractionGeneratorOptions.DEFAULT)
            doRefactor(configuration, onFinish)
        }

        override fun getIdentifierError(
            file: KtFile,
            variableRange: TextRange
        ): String? {
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

        override fun supportConfigurableOptions(): Boolean = false
    }

    override fun restart(
        templateState: TemplateState,
        file: KtFile,
        restartInplace: Boolean
    ): Boolean {
        if (helper is InplaceExtractionHelper) {
            helper.restart(templateState, file, restartInplace)
            return true
        }
        return false
    }

    override fun doInvoke(
        editor: Editor,
        file: KtFile,
        elements: List<PsiElement>,
        targetSibling: PsiElement
    ) {
        val adjustedElements = elements.singleOrNull().safeAs<KtBlockExpression>()?.statements ?: elements
        if (adjustedElements.isEmpty()) return
        nonBlocking(file.project, {
            ExtractionData(file, adjustedElements.toRange(false), targetSibling)
        }) { extractionData ->
            ExtractionEngine(helper).run(editor, extractionData)
        }
    }
}