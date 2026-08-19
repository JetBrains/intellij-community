// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch.ui

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.core.script.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.core.script.scratch.ScratchFile
import org.jetbrains.kotlin.idea.core.script.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.core.script.scratch.output.PreviewEditorScratchOutputHandler
import org.jetbrains.kotlin.idea.core.script.scratch.output.PreviewOutputBlocksManager
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.psi.UserDataProperty

abstract class ScratchFileEditorWithPreview(
    kotlinScratchFile: ScratchFile,
    sourceTextEditor: TextEditor,
    previewTextEditor: TextEditor,
    initialLayout: Layout? = null,
) : TextEditorWithPreview(
    sourceTextEditor,
    previewTextEditor,
    defaultLayout = Layout.SHOW_EDITOR,
    layout = initialLayout,
), TextEditor, ScratchEditorLinesTranslator {

    val scratchFile: ScratchFile = kotlinScratchFile

    private val sourceEditor = sourceTextEditor.editor as EditorEx
    private val _previewEditor = previewTextEditor.editor as EditorEx
    private val previewOutputManager: PreviewOutputBlocksManager = PreviewOutputBlocksManager(_previewEditor)

    protected val toolWindowHandler: ScratchOutputHandler = requestOutputHandler()
    protected val previewEditorScratchOutputHandler: PreviewEditorScratchOutputHandler = PreviewEditorScratchOutputHandler(
        previewOutputManager, toolWindowHandler, previewTextEditor as Disposable
    )

    protected abstract fun requestOutputHandler(): ScratchOutputHandler

    init {
        sourceTextEditor.parentScratchEditorWithPreview = this
        previewTextEditor.parentScratchEditorWithPreview = this

        configureSyncScrollForSourceAndPreview()
        configureSyncHighlighting(sourceEditor, _previewEditor, translator = this)

        ScratchFileAutoRunner.addListener(kotlinScratchFile.project, sourceTextEditor)
    }

    override fun getFile(): VirtualFile = scratchFile.virtualFile

    override fun previewLineToSourceLines(previewLine: Int): Pair<Int, Int>? {
        val expressionUnderCaret = scratchFile.getExpressionAtLine(previewLine) ?: return null
        val outputBlock = previewOutputManager.getBlock(expressionUnderCaret) ?: return null

        return outputBlock.lineStart to outputBlock.lineEnd
    }

    override fun sourceLineToPreviewLines(sourceLine: Int): Pair<Int, Int>? {
        val block = previewOutputManager.getBlockAtLine(sourceLine) ?: return null
        if (!block.sourceExpression.linesInformationIsCorrect()) return null

        return block.sourceExpression.lineStart to block.sourceExpression.lineEnd
    }

    private fun configureSyncScrollForSourceAndPreview() {
        val scrollable = object : BaseSyncScrollable() {
            override fun processHelper(helper: ScrollHelper) {
                if (!helper.process(0, 0)) return

                val alignments = previewOutputManager.computeSourceToPreviewAlignments()

                for ((fromSource, fromPreview) in alignments) {
                    if (!helper.process(fromSource, fromPreview)) return
                    if (!helper.process(fromSource, fromPreview)) return
                }

                helper.process(sourceEditor.document.lineCount, _previewEditor.document.lineCount)
            }

            override fun isSyncScrollEnabled(): Boolean = true
        }

        val scrollSupport = SyncScrollSupport.TwosideSyncScrollSupport(listOf(sourceEditor, _previewEditor), scrollable)
        val listener = VisibleAreaListener { e -> scrollSupport.visibleAreaChanged(e) }

        sourceEditor.scrollingModel.addVisibleAreaListener(listener)
        _previewEditor.scrollingModel.addVisibleAreaListener(listener)
    }

    override fun navigateTo(navigatable: Navigatable) {
        myEditor.navigateTo(navigatable)
    }

    override fun canNavigateTo(navigatable: Navigatable): Boolean {
        return myEditor.canNavigateTo(navigatable)
    }

    override fun getEditor(): Editor {
        return myEditor.editor
    }

    fun clearOutputHandlers() {
        previewEditorScratchOutputHandler.clear(scratchFile)
    }

    override val isShowActionsInTabs: Boolean
        get() = false

    override fun createViewActionGroup(): ActionGroup {
        return DefaultActionGroup()
    }

    fun setExplainModeEnabled(isExplainEnabled: Boolean) {
        saveExplainOption(isExplainEnabled)
        val targetLayout = if (isExplainEnabled) Layout.SHOW_EDITOR_AND_PREVIEW else Layout.SHOW_EDITOR
        if (getLayout() != targetLayout) {
            setLayout(targetLayout)
        }
        ActivityTracker.getInstance().inc()
    }

    private fun syncExplainOptionWithLayout(layout: Layout?) {
        val isExplainEnabled = layout == Layout.SHOW_EDITOR_AND_PREVIEW
        if (scratchFile.options.isExplainEnabled != isExplainEnabled) {
            saveExplainOption(isExplainEnabled)
            ActivityTracker.getInstance().inc()
        }
    }

    private fun saveExplainOption(isExplainEnabled: Boolean) {
        if (scratchFile.options.isExplainEnabled != isExplainEnabled) {
            scratchFile.saveOptions { copy(isExplainEnabled = isExplainEnabled) }
        }
    }

    override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {
        when {
            oldValue != newValue -> clearOutputHandlers()
        }
        syncExplainOptionWithLayout(newValue)
    }

    @TestOnly
    fun setPreviewEnabled(isPreviewEnabled: Boolean) {
        setLayout(if (isPreviewEnabled) Layout.SHOW_EDITOR_AND_PREVIEW else Layout.SHOW_EDITOR)
    }

    @TestOnly
    fun dumpExplainContent(): String = previewOutputManager.dumpContent()
}

fun TextEditor.findScratchFileEditorWithPreview(): ScratchFileEditorWithPreview? =
    this as? ScratchFileEditorWithPreview ?: parentScratchEditorWithPreview

var TextEditor.parentScratchEditorWithPreview: ScratchFileEditorWithPreview? by UserDataProperty(Key.create("parent.preview.editor"))


/**
 * Checks if [ScratchExpression.element] is actually starts at the [ScratchExpression.lineStart]
 * and ends at the [ScratchExpression.lineEnd].
 */
fun ScratchExpression.linesInformationIsCorrect(): Boolean {
    if (!element.isValid) return false
    return element.getLineNumber(start = true) == lineStart && element.getLineNumber(start = false) == lineEnd
}
