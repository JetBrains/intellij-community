// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions.internal.resolutionDebugging

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.ui.ScreenUtil
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.Printer

@Suppress("HardCodedStringLiteral")
class DebugTypeResolutionAction : AnAction("Debug Resolution of Type at Caret") {
    override fun actionPerformed(e: AnActionEvent) {
        if (!ApplicationManager.getApplication().isInternal) return

        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile
            ?: return somethingWentWrong("Can't get a KtFile at current location. Are you in .kt-file?")
        val caret = e.getData(CommonDataKeys.CARET)
            ?: return somethingWentWrong("Can't get a position of caret in file", psiFile)

        val expression = psiFile.findElementAt(caret.offset)?.getNonStrictParentOfType<KtNameReferenceExpression>()
            ?: return somethingWentWrong("Can't find a KtExpression at the caret. Check that the caret is at an expression", psiFile, caret)

        val bindingContext = psiFile.analyzeWithAllCompilerChecks().bindingContext

        val actualType = bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, expression)?.type
            ?: return somethingWentWrong("Couldn't get a type from EXPRESSION_TYPE_INFO slice of BindingContext", psiFile, caret, expression)

        val expectedType = expression.parentsWithSelf.firstNotNullOfOrNull {
            if (it is KtExpression) bindingContext.get(BindingContext.EXPECTED_EXPRESSION_TYPE, it) else null
        } ?: return somethingWentWrong(
                "Couldn't get a type from the EXPECTED_EXPRESSION_TYPE slice of BindingContext",
                psiFile,
                caret,
                expression
            )

        buildAndShowReport(expectedType, actualType)
    }

    private fun somethingWentWrong(message: String, psiFile: KtFile? = null, caret: Caret? = null, expression: KtExpression? = null) {
        showReportWindow("""
            Something went wrong!
            
            $message
            
            Context:
            psiFile=${psiFile}
            caret=${caret}
            expression=${expression?.text}
        """.trimIndent())
    }

    private fun buildAndShowReport(
        expected: KotlinType,
        actual: KotlinType
    ) {
        val ctx = ReportContext()
        val report = ctx.TypeMismatchDebugReport(expected, actual)
        val reportRendered = with(report) { ctx.render() }

        showReportWindow(reportRendered)
    }


    private fun showReportWindow(content: String) {
        val screenBounds = ScreenUtil.getMainScreenBounds()
        val width = (screenBounds.width * 0.8).toInt()
        val height = (screenBounds.height * 0.8).toInt()
        val window = panel {
            row {
                textArea()
                    .align(Align.FILL)
                    .applyToComponent { text = content }
                    .resizableColumn()
            }.resizableRow()
        }.withPreferredSize(width, height)

        dialog("Debug Mismatch", window).showAndGet()
    }
}

internal data class TypeMismatchDebugReport(
    val expectedTypeReference: KotlinTypeReference,
    val actualTypeReference: KotlinTypeReference,
) {
    fun ReportContext.render(): String {
        val printer = Printer(StringBuilder())
        with(printer) {
            println("Expected type: $expectedTypeReference")
            println()
            println("Actual type: $actualTypeReference")
            println()
            println("=== Context ===")
            println()
            renderContextReport()
        }
        return printer.toString()
    }
}

internal fun ReportContext.TypeMismatchDebugReport(expected: KotlinType, actual: KotlinType): TypeMismatchDebugReport {
    return TypeMismatchDebugReport(expected.referenceToInstance(), actual.referenceToInstance())
}
