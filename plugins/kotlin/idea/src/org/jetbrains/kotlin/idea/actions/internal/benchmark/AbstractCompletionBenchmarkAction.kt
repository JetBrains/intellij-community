// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions.internal.benchmark

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.navigation.openFileWithPsiElement
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleOrigin
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.completion.CompletionBenchmarkSink
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.KtFile
import java.util.*
import javax.swing.JFileChooser
import javax.swing.JPanel

abstract class AbstractCompletionBenchmarkAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val benchmarkSink = CompletionBenchmarkSink.enableAndGet()
        val scenario = createBenchmarkScenario(project, benchmarkSink) ?: return

        KotlinPluginDisposable.getInstance(project).coroutineScope.launch(Dispatchers.EDT) {
            scenario.doBenchmark()
            CompletionBenchmarkSink.disable()
        }
    }

    internal abstract fun createBenchmarkScenario(
        project: Project,
        benchmarkSink: CompletionBenchmarkSink.Impl
    ): AbstractCompletionBenchmarkScenario?

    companion object {
        fun showPopup(project: Project, @Nls text: String) {
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(text, MessageType.ERROR, null)
                .setFadeoutTime(5000)
                .createBalloon().showInCenterOf(statusBar.component)
        }

        internal fun <T> List<T>.randomElement(random: Random): T? = if (this.isNotEmpty()) this[random.nextInt(this.size)] else null
        internal fun <T : Any> List<T>.shuffledSequence(random: Random): Sequence<T> =
            generateSequence { this.randomElement(random) }.distinct()

        internal fun collectSuitableKotlinFiles(project: Project, filePredicate: (KtFile) -> Boolean): MutableList<KtFile> {
            val scope = GlobalSearchScope.allScope(project)

            fun KtFile.isUsableForBenchmark(): Boolean {
                val moduleInfo = this.moduleInfoOrNull ?: return false
                if (this.isCompiled || !this.isWritable || this.isScript()) return false
                return moduleInfo.moduleOrigin == ModuleOrigin.MODULE
            }

            val kotlinVFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)

            return kotlinVFiles
                .asSequence()
                .mapNotNull { vfile -> (vfile.toPsiFile(project) as? KtFile) }
                .filterTo(mutableListOf()) { it.isUsableForBenchmark() && filePredicate(it) }
        }

        internal fun JPanel.addBoxWithLabel(@Nls tooltip: String, @Nls label: String = "$tooltip:", default: String, i: Int): JBTextField {
            this.add(JBLabel(label), GridConstraints().apply { row = i; column = 0 })
            val textField = JBTextField().apply {
                text = default
                toolTipText = tooltip
            }
            this.add(textField, GridConstraints().apply { row = i; column = 1; fill = GridConstraints.FILL_HORIZONTAL })
            return textField
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isApplicationInternalMode()
    }
}

internal abstract class AbstractCompletionBenchmarkScenario(
    val project: Project, private val benchmarkSink: CompletionBenchmarkSink.Impl,
    val random: Random = Random(), private val timeout: Long = 15000
) {


    sealed class Result {
        abstract fun toCSV(stringBuilder: StringBuilder)

        open class SuccessResult(val lines: Int, val filePath: String, val first: Long, val full: Long) : Result() {
            override fun toCSV(stringBuilder: StringBuilder): Unit = with(stringBuilder) {
                append(filePath)
                append(", ")
                append(lines)
                append(", ")
                append(first)
                append(", ")
                append(full)
            }
        }

        class ErrorResult(val filePath: String) : Result() {
            override fun toCSV(stringBuilder: StringBuilder): Unit = with(stringBuilder) {
                append(filePath)
                append(", ")
                append(", ")
                append(", ")
            }
        }
    }


    protected suspend fun typeAtOffsetAndGetResult(text: String, offset: Int, file: KtFile): Result {
        openFileWithPsiElement(file.navigationElement, false, true)

        val document =
            PsiDocumentManager.getInstance(project).getDocument(file) ?: return Result.ErrorResult("${file.virtualFile.path}:O$offset")

        val location = "${file.virtualFile.path}:${document.getLineNumber(offset)}"

        val editor = EditorFactory.getInstance().getEditors(document, project).firstOrNull() ?: return Result.ErrorResult(location)


        delay(500)

        editor.moveCaret(offset, scrollType = ScrollType.CENTER)

        delay(500)

        CommandProcessor.getInstance().executeCommand(project, {
            runWriteAction {
                document.insertString(editor.caretModel.offset, "\n$text\n")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
            editor.moveCaret(editor.caretModel.offset + text.length + 1)
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC, null)
        }, "InsertTextAndInvokeCompletion", "completionBenchmark")

        val result = try {
            withTimeout(timeout) { collectResult(file, location) }
        } catch (_: CancellationException) {
            Result.ErrorResult(location)
        }

        CommandProcessor.getInstance().executeCommand(project, {
            runWriteAction {
                document.deleteString(offset, offset + text.length + 2)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }, "RevertToOriginal", "completionBenchmark")

        delay(100)
        return result
    }

    private suspend fun collectResult(file: KtFile, location: String): Result {
        val results = benchmarkSink.channel.receive()
        return Result.SuccessResult(file.getLineCount(), location, results.firstFlush, results.full)
    }

    protected fun saveResults(allResults: List<Result>) {
        val jfc = JFileChooser()
        val result = jfc.showSaveDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = jfc.selectedFile
            file.writeText(buildString {
                appendLine("n, file, lines, ff, full")
                var i = 0
                allResults.forEach {
                    append(i++)
                    append(", ")
                    it.toCSV(this)
                    appendLine()
                }
            })
        }
        AbstractCompletionBenchmarkAction.showPopup(project, KotlinBundle.message("title.done"))
    }

    abstract suspend fun doBenchmark()
}
