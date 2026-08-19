// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch.ui

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchBundle
import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchFile
import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchFileAutoRunner
import org.jetbrains.kotlin.idea.core.script.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.core.script.scratch.actions.RunKotlinScratchAction
import org.jetbrains.kotlin.idea.core.script.scratch.output.PreviewEditorScratchOutputHandler
import org.jetbrains.kotlin.idea.core.script.scratch.output.PreviewOutputBlocksManager
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchToolWindowHandlerKeeper
import org.jetbrains.kotlin.psi.UserDataProperty

class KotlinScratchFileEditorWithPreview(
    val kotlinScratchFile: KotlinScratchFile,
    sourceTextEditor: TextEditor,
    previewTextEditor: TextEditor,
) : TextEditorWithPreview(
    sourceTextEditor,
    previewTextEditor,
    defaultLayout = Layout.SHOW_EDITOR,
    layout = if (kotlinScratchFile.options.isExplainEnabled) {
        Layout.SHOW_EDITOR_AND_PREVIEW
    } else {
        Layout.SHOW_EDITOR
    },
), TextEditor {

    private val sourceEditor = sourceTextEditor.editor as EditorEx
    private val _previewEditor = previewTextEditor.editor as EditorEx
    private val previewOutputManager: PreviewOutputBlocksManager = PreviewOutputBlocksManager(_previewEditor)

    private val toolWindowHandler: ScratchOutputHandler = ScratchToolWindowHandlerKeeper.requestOutputHandler()
    private val previewEditorScratchOutputHandler: PreviewEditorScratchOutputHandler = PreviewEditorScratchOutputHandler(
        previewOutputManager, toolWindowHandler, previewTextEditor as Disposable
    )

    init {
        kotlinScratchFile.executor.addOutputHandler(previewEditorScratchOutputHandler)

        sourceTextEditor.parentScratchEditorWithPreview = this
        previewTextEditor.parentScratchEditorWithPreview = this

        configureSyncScrollForSourceAndPreview()
        configureSyncHighlighting(sourceEditor, _previewEditor)

        sourceTextEditor.editor.document.addDocumentListener(
            kotlinScratchFile.project.service<KotlinScratchFileAutoRunner>(),
            sourceTextEditor,
        )
    }

    override fun dispose() {
        kotlinScratchFile.executor.stop()
        ScratchToolWindowHandlerKeeper.releaseOutputHandler(toolWindowHandler)
        super.dispose()
    }

    override fun createToolbar(): ActionToolbar {
        val toolbarGroup = DefaultActionGroup().apply {
            addAction(RunKotlinScratchAction())
            addAction(ClearScratchAction())
            addSeparator()
            addAction(ModulesComboBoxAction(kotlinScratchFile))
            addAction(JdksComboBoxAction(kotlinScratchFile))
        }
        return ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)
    }

    override fun createViewActionGroup(): ActionGroup = DefaultActionGroup(
        MakeBeforeRunToggleAction(),
        InteractiveModeToggleAction(),
        ExplainCodeModeToggleAction(),
    )

    private inner class MakeBeforeRunToggleAction : ToggleAction() {
        init {
            templatePresentation.text = KotlinScratchBundle.message("scratch.toggle.make.before.run.text")
            templatePresentation.description = KotlinScratchBundle.message("scratch.toggle.make.before.run.description")
            templatePresentation.icon = AllIcons.Actions.Compile
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return kotlinScratchFile.options.isMakeBeforeRun
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (kotlinScratchFile.module == null || kotlinScratchFile.options.isInteractiveMode) return
            kotlinScratchFile.saveOptions { copy(isMakeBeforeRun = state) }
            ActivityTracker.getInstance().inc()
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            val selectedModule = kotlinScratchFile.module?.takeIf { !it.isDisposed }
            val isInteractiveMode = kotlinScratchFile.options.isInteractiveMode
            e.presentation.icon = AllIcons.Actions.Compile
            e.presentation.isVisible = selectedModule != null
            e.presentation.isEnabled = selectedModule != null && !isInteractiveMode
            val disabledByInteractiveModeMessage =
                KotlinScratchBundle.message("scratch.toggle.make.before.run.interactive.disabled.description")
            e.presentation.text = if (isInteractiveMode) disabledByInteractiveModeMessage else templatePresentation.text
            e.presentation.description = when {
                isInteractiveMode -> disabledByInteractiveModeMessage
                selectedModule != null -> KotlinScratchBundle.message(
                    "scratch.toggle.make.before.run.module.description", selectedModule.name
                )

                else -> KotlinScratchBundle.message("scratch.toggle.make.before.run.disabled.description")
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class InteractiveModeToggleAction : ToggleAction() {
        init {
            templatePresentation.text = KotlinScratchBundle.message("scratch.toggle.interactive.mode.text")
            templatePresentation.description = KotlinScratchBundle.message(
                "scratch.toggle.interactive.mode.description",
                KotlinScratchFileAutoRunner.AUTO_RUN_DELAY_MS / 1000,
            )
            templatePresentation.icon = AllIcons.Actions.Lightning
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return kotlinScratchFile.options.isInteractiveMode
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            kotlinScratchFile.saveOptions { copy(isInteractiveMode = state) }
            ActivityTracker.getInstance().inc()
            if (state) {
                kotlinScratchFile.project.service<KotlinScratchFileAutoRunner>().submitRun(kotlinScratchFile)
            }
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.icon = AllIcons.Actions.Lightning
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    private inner class ExplainCodeModeToggleAction : ToggleAction() {
        init {
            templatePresentation.text = KotlinScratchBundle.message("scratch.toggle.explain.mode.text")
            templatePresentation.description = KotlinScratchBundle.message("scratch.toggle.explain.mode.description")
            templatePresentation.icon = AllIcons.General.InspectionsEye
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            return kotlinScratchFile.options.isExplainEnabled
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            setExplainModeEnabled(state)
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.icon = AllIcons.General.InspectionsEye
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    override fun getFile(): VirtualFile = kotlinScratchFile.virtualFile

    private fun configureSyncScrollForSourceAndPreview() {
        val scrollable = object : BaseSyncScrollable() {
            override fun processHelper(helper: ScrollHelper) {
                if (!helper.process(0, 0)) return

                helper.process(sourceEditor.document.lineCount, _previewEditor.document.lineCount)
            }

            override fun isSyncScrollEnabled(): Boolean = true
        }

        val scrollSupport = SyncScrollSupport.TwosideSyncScrollSupport(listOf(sourceEditor, _previewEditor), scrollable)
        val listener = VisibleAreaListener { e -> scrollSupport.visibleAreaChanged(e) }

        sourceEditor.scrollingModel.addVisibleAreaListener(listener)
        _previewEditor.scrollingModel.addVisibleAreaListener(listener)
    }

    private fun configureSyncHighlighting(sourceEditor: EditorEx, previewEditor: EditorEx) {
        val exclusiveCaretHighlightingListener = object : FocusChangeListener {
            override fun focusLost(editor: Editor) {}

            override fun focusGained(editor: Editor) {
                sourceEditor.settings.isCaretRowShown = false
                previewEditor.settings.isCaretRowShown = false

                editor.settings.isCaretRowShown = true
            }
        }
        sourceEditor.addFocusListener(exclusiveCaretHighlightingListener)
        previewEditor.addFocusListener(exclusiveCaretHighlightingListener)
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
        previewEditorScratchOutputHandler.clear(kotlinScratchFile)
    }

    override val isShowActionsInTabs: Boolean
        get() = false


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
        if (kotlinScratchFile.options.isExplainEnabled != isExplainEnabled) {
            saveExplainOption(isExplainEnabled)
            ActivityTracker.getInstance().inc()
        }
    }

    private fun saveExplainOption(isExplainEnabled: Boolean) {
        if (kotlinScratchFile.options.isExplainEnabled != isExplainEnabled) {
            kotlinScratchFile.saveOptions { copy(isExplainEnabled = isExplainEnabled) }
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

    @TestOnly
    fun getViewActionsForTesting(): List<AnAction> = createViewActionGroup().getChildren(null).toList()
}

fun TextEditor.findScratchFileEditorWithPreview(): KotlinScratchFileEditorWithPreview? =
    this as? KotlinScratchFileEditorWithPreview ?: parentScratchEditorWithPreview

var TextEditor.parentScratchEditorWithPreview: KotlinScratchFileEditorWithPreview? by UserDataProperty(Key.create("parent.preview.editor"))

