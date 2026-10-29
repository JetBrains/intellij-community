// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.notifications

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.SplitEditorToolbar
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.JBColor
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.core.script.definition.SCRIPT_EDITOR_TOOLBAR_GROUP_ID
import org.jetbrains.kotlin.idea.core.script.definition.ScriptEditorToolbarProvider
import org.jetbrains.kotlin.idea.core.script.definition.editorToolbar
import org.jetbrains.kotlin.idea.core.script.definition.kotlinScriptTemplate
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettingsConfigurable
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

/** Shows the [editorToolbar] of the script definition and disposes it when the provider changes. */
internal class ScriptEditorToolbarNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (file.isNonScript()) return null
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        if (!ktFile.isScript()) return null
        val provider = ktFile.findScriptDefinition()?.compilationConfiguration?.get(ScriptCompilationConfiguration.ide.editorToolbar)

        return Function { fileEditor -> if (fileEditor is TextEditor) fileEditor.showToolbar(provider) else null }
    }
}

/** Shows the [SCRIPT_EDITOR_TOOLBAR_GROUP_ID] group and the scripting settings. */
internal object DefaultScriptEditorToolbarProvider : ScriptEditorToolbarProvider {
    override fun provide(fileEditor: FileEditor, lifetime: Disposable): JComponent? {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction(SCRIPT_EDITOR_TOOLBAR_GROUP_ID) as? ActionGroup ?: return null
        val settings = actionManager.getAction(OPEN_SCRIPTING_SETTINGS_ACTION_ID) ?: return createScriptEditorToolbar(fileEditor, group)
        return createScriptEditorToolbar(fileEditor, group, DefaultActionGroup(settings))
    }
}

private fun createScriptEditorToolbar(fileEditor: FileEditor, left: ActionGroup, right: ActionGroup = DefaultActionGroup()): JComponent {
    val target = fileEditor.preferredFocusedComponent ?: fileEditor.component
    fun toolbar(group: ActionGroup) = ActionManager.getInstance().createActionToolbar(SCRIPT_EDITOR_TOOLBAR_PLACE, group, true).apply {
        targetComponent = target
        isReservePlaceAutoPopupIcon = false
    }
    val separator = JBColor.namedColor("EditorTabs.underTabsBorderColor", JBUI.CurrentTheme.EditorTabs.borderColor())
    return SplitEditorToolbar(toolbar(left), toolbar(right)).apply { border = CustomLineBorder(separator, 0, 0, 1, 0) }
}

private class ShownToolbar(val provider: ScriptEditorToolbarProvider, val component: JComponent?, val lifetime: Disposable)

private val SHOWN_TOOLBAR: Key<ShownToolbar> = Key.create("kotlin.script.editor.toolbar")

private fun FileEditor.showToolbar(provider: ScriptEditorToolbarProvider?): JComponent? {
    val shown = getUserData(SHOWN_TOOLBAR)
    if (shown != null && shown.provider === provider) return shown.component

    putUserData(SHOWN_TOOLBAR, null)
    shown?.let { Disposer.dispose(it.lifetime) }
    if (provider == null) return null

    val lifetime = Disposer.newDisposable("Kotlin script editor toolbar")
    if (!Disposer.tryRegister(this, lifetime)) return null
    val component = provider.provide(this, lifetime)
    putUserData(SHOWN_TOOLBAR, ShownToolbar(provider, component, lifetime))
    return component
}

/** Shows the definition name and its file pattern, as the scripting settings do. */
internal class ScriptDefinitionLabelAction : AnAction(), CustomComponentAction {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun update(e: AnActionEvent) {
        val definition = (e.getData(CommonDataKeys.PSI_FILE) as? KtFile)?.findScriptDefinition()
        e.presentation.isEnabledAndVisible = definition != null
        if (definition == null) return

        @NlsSafe val name = definition.name
        @NlsSafe val pattern =
            definition.compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptTemplate]?.title?.takeIf { it.isNotBlank() }
                ?: ".${definition.fileExtension}"
        e.presentation.text = name
        e.presentation.putClientProperty(NAME, name)
        e.presentation.putClientProperty(PATTERN, pattern)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
        JPanel(HorizontalLayout(JBUI.scale(6))).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6)
            add(JBLabel())
            add(JBLabel().apply { foreground = UIUtil.getContextHelpForeground() })
        }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
        @NlsSafe val name = presentation.getClientProperty(NAME)
        @NlsSafe val pattern = presentation.getClientProperty(PATTERN)
        (component.getComponent(0) as JBLabel).text = name
        (component.getComponent(1) as JBLabel).text = pattern
    }
}

internal class ShowScriptStructureAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.STRUCTURE_VIEW)?.activate(null)
    }
}

internal class OpenScriptingSettingsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinScriptingSettingsConfigurable::class.java)
    }
}

private const val OPEN_SCRIPTING_SETTINGS_ACTION_ID = "KotlinScripts.OpenScriptingSettings"

private const val SCRIPT_EDITOR_TOOLBAR_PLACE = "KotlinScriptEditorToolbar"

private val NAME: Key<String> = Key.create("kotlin.script.definition.name")

private val PATTERN: Key<String> = Key.create("kotlin.script.definition.pattern")
