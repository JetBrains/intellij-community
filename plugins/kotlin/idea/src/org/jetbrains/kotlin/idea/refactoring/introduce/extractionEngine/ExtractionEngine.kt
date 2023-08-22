// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.nonBlocking
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.swing.event.HyperlinkEvent

abstract class ExtractionEngineHelper(@NlsContexts.DialogTitle val operationName: String) {
    open fun adjustExtractionData(data: ExtractionData): ExtractionData = data

    fun doRefactor(config: ExtractionGeneratorConfiguration, onFinish: (ExtractionResult) -> Unit = {}) {
        val project = config.descriptor.extractionData.project
        onFinish(project.executeWriteCommand<ExtractionResult>(operationName) { config.generateDeclaration() })
    }

    open fun validate(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptorWithConflicts = descriptor.validate()

    abstract fun configureAndRun(
        project: Project,
        editor: Editor,
        descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
        onFinish: (ExtractionResult) -> Unit = {}
    )
}

class ExtractionEngine(
    val helper: ExtractionEngineHelper
) {
    fun run(
        editor: Editor,
        extractionData: ExtractionData,
        onFinish: (ExtractionResult) -> Unit = {}
    ) {
        val project = extractionData.project

        val adjustExtractionData = helper.adjustExtractionData(extractionData)
        val analysisResult = ActionUtil.underModalProgress(project, KotlinBundle.message("progress.title.analyze.extraction.data")) {
            adjustExtractionData.performAnalysis()
        }

        if (isUnitTestMode() && analysisResult.status != AnalysisResult.Status.SUCCESS) {
            throw BaseRefactoringProcessor.ConflictsInTestsException(analysisResult.messages.map { it.renderMessage() })
        }

        fun validateAndRefactor() {
            nonBlocking(project, {
                try {
                    helper.validate(analysisResult.descriptor!!)
                } catch (e: RuntimeException) {
                    ExtractableCodeDescriptorWithException(e)
                }
            }) { result ->
                result.safeAs<ExtractableCodeDescriptorWithException>()?.let { throw it.exception }
                val validationResult = result.cast<ExtractableCodeDescriptorWithConflicts>()
                project.checkConflictsInteractively(validationResult.conflicts) {
                    helper.configureAndRun(project, editor, validationResult) {
                        try {
                            onFinish(it)
                        } finally {
                            it.dispose()
                            extractionData.dispose()
                        }
                    }
                }
            }
        }

        val message = analysisResult.messages.joinToString("\n") { it.renderMessage() }
        when (analysisResult.status) {
            AnalysisResult.Status.CRITICAL_ERROR -> {
                showErrorHint(project, editor, message, helper.operationName)
            }

            AnalysisResult.Status.NON_CRITICAL_ERROR -> {
                val anchorPoint = RelativePoint(
                    editor.contentComponent,
                    editor.visualPositionToXY(editor.selectionModel.selectionStartPosition!!)
                )
                @NlsSafe val htmlContent =
                    "$message<br/><br/><a href=\"EXTRACT\">${KotlinBundle.message("text.proceed.with.extraction")}</a>"
                JBPopupFactory.getInstance()!!
                    .createHtmlTextBalloonBuilder(
                        htmlContent,
                        MessageType.WARNING
                    ) { event ->
                        if (event?.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                            validateAndRefactor()
                        }
                    }
                    .setHideOnClickOutside(true)
                    .setHideOnFrameResize(false)
                    .setHideOnLinkClick(true)
                    .createBalloon()
                    .show(anchorPoint, Balloon.Position.below)
            }

            AnalysisResult.Status.SUCCESS -> validateAndRefactor()
        }
    }
}
