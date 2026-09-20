// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.settings

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.BrowserUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ide.setToolTipText
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.CollectionListModel
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.configuration.KOTLIN_SCRIPTING_SETTINGS_ID
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import java.awt.Component
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/** Beyond this the editor scrolls, so a long list never makes the page taller. */
private const val MAX_VISIBLE_LIST_ROWS: Int = 4

private val reloadDisabledTooltip: HtmlChunk
    get() = HtmlChunk.text(KotlinBaseScriptingBundle.message("script.definitions.reload.disabled.tooltip"))

internal const val DISCOVERY_DOCUMENTATION_URL: String = "https://kotlinlang.org/docs/custom-script-deps-tutorial.html"

internal class KotlinScriptingSettingsConfigurable(val project: Project) : SearchableConfigurable {

    private val tableView = TableView(ScriptDefinitionTable(mutableListOf())).apply {
        // A header and a column grid read as a spreadsheet. A definition row carries its own detail.
        tableHeader = null
        showVerticalLines = false
        rowHeight = JBUI.scale(DEFINITION_ROW_HEIGHT)
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setEmptyState(KotlinBundle.message("status.text.no.definitions"))
        emptyText.isShowAboveCenter = false
    }

    private val definitionClassesModel = CollectionListModel<String>()
    private val classpathModel = CollectionListModel<String>()

    private val definitionClassesList = JBList(definitionClassesModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = definitionListRenderer()
        setEmptyState(KotlinBaseScriptingBundle.message("manual.loading.definition.classes.empty"))
        // `StatusText` puts its text a third of the way down by default, which reads as a wrong margin.
        emptyText.isShowAboveCenter = false
    }

    private val classpathList = JBList(classpathModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        cellRenderer = definitionListRenderer()
        setEmptyState(KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.empty"))
        emptyText.isShowAboveCenter = false
    }

    private val definitionClassesPanel: JComponent = ToolbarDecorator.createDecorator(definitionClassesList)
        .setAddAction { addDefinitionClass() }
        .setEditAction { editDefinitionClass() }
        .disableUpDownActions()
        .createPanel()

    private val classpathPanel: JComponent = ToolbarDecorator.createDecorator(classpathList)
        .setAddAction { addClasspathEntries(chooseJars()) }
        .setAddActionName(KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.add.jar"))
        .addExtraAction(AddClasspathDirectoryAction())
        .setEditAction { editClasspathEntry() }
        .disableUpDownActions()
        .createPanel()

    private val reloadButton = JButton(
        KotlinBaseScriptingBundle.message("script.definitions.reload.button"), AllIcons.Actions.Refresh
    ).apply {
        addActionListener { reloadDefinitions() }
    }

    /**
     * Carries the reason the button is off, because a disabled button may get no mouse event.
     *
     * Which component the hover reaches depends on the toolkit, so the button holds the same text
     * while it is off. See [updateReloadAvailability].
     */
    private val reloadButtonPanel = Wrapper(reloadButton).apply {
        setToolTipText(reloadDisabledTooltip)
    }

    private var appliedSnapshot = ScriptDefinitionsSnapshot(emptyList(), emptyList(), emptyList())

    override fun createComponent(): JComponent {
        val panel = panel {
            customizeSpacingConfiguration(object : IntelliJSpacingConfiguration() {
                // Each row below states its own gap. A component that adds one of its own would
                // stack on top of it, because a row gap and a component gap are separate.
                override val verticalComponentGap: Int = 0
            }) {
                row {
                    // The link lives inside the sentence, so it stays at its end however the text wraps.
                    text(KotlinBaseScriptingBundle.message("script.definitions.discovery.explanation")) {
                        BrowserUtil.browse(DISCOVERY_DOCUMENTATION_URL)
                    }.resizableColumn()
                    cell(reloadButtonPanel).align(AlignX.RIGHT).align(AlignY.CENTER)
                }.customize(UnscaledGapsY(top = 8))
                row {
                    cell(ToolbarDecorator.createDecorator(tableView).disableAddAction().disableRemoveAction().createPanel())
                        .align(Align.FILL)
                }.customize(UnscaledGapsY(top = 12))
                row {
                    cell(hint(KotlinBaseScriptingBundle.message("script.definitions.precedence.comment")))
                }.customize(UnscaledGapsY(top = 8))

                group(KotlinBaseScriptingBundle.message("manual.loading.title"), indent = false) {
                    definitionField(
                        KotlinBaseScriptingBundle.message("manual.loading.definition.classes"),
                        KotlinBaseScriptingBundle.message("manual.loading.definition.classes.hint"),
                        definitionClassesPanel,
                    )
                    definitionField(
                        KotlinBaseScriptingBundle.message("manual.loading.definition.classpath"),
                        KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.hint"),
                        classpathPanel,
                    )
                }.customize(UnscaledGapsY(top = 24))
            }
        }


        installDirtyListeners()
        bindCompactHeight(definitionClassesList, definitionClassesModel, definitionClassesPanel)
        bindCompactHeight(classpathList, classpathModel, classpathPanel)
        reset()
        return panel
    }

    override fun reset() {
        showSnapshot(readSnapshot() ?: return)
    }

    override fun apply() {
        if (!isModified()) return

        stopEditing()
        val edited = currentSnapshot()
        KotlinScriptingSettings.getInstance(project).update { edited.toSettingsState() }
        appliedSnapshot = edited.detached()
        updateReloadAvailability()

        KotlinScriptService.getInstance(project).scheduleReloadOpenScripts()
    }

    override fun isModified(): Boolean = currentSnapshot() != appliedSnapshot

    override fun getDisplayName(): String = KotlinBundle.message("script.name.kotlin.scripting")

    override fun getId(): String = KOTLIN_SCRIPTING_SETTINGS_ID

    /**
     * The reload runs in the background, never under a modal progress.
     */
    private fun reloadDefinitions() {
        val modality = ModalityState.stateForComponent(reloadButton)
        KotlinScriptService.getInstance(project).scheduleReloadOpenScripts().invokeOnCompletion { failure ->
            if (failure != null) return@invokeOnCompletion
            ApplicationManager.getApplication().invokeLater({
                // The balloon comes first. Reading the new list opens a modal progress of its own,
                // and a balloon shown right after one closes never reaches the screen.
                showReloadDoneBalloon()
                showSnapshot(readSnapshot() ?: return@invokeLater)
            }, modality, project.disposed)
        }
    }

    /**
     * Reports the reload with a balloon, and never with a notification.
     */
    @Suppress("SplitModeApiUsage")
    private fun showReloadDoneBalloon() {
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
                KotlinBaseScriptingBundle.message("script.definitions.reload.done"),
                MessageType.INFO,
                null,
            )
            .setFadeoutTime(3000)
            .createBalloon()
            .show(RelativePoint.getSouthOf(reloadButton), Balloon.Position.below)
    }

    private fun readSnapshot(): ScriptDefinitionsSnapshot? =
        underModalProgress(KotlinBaseScriptingBundle.message("looking.for.script.definitions.in.classpath")) {
            readScriptDefinitionsSnapshot(project)
        }

    /**
     * Runs [read] off the EDT and returns immutable data. The caller mutates the components.
     * A failure is reported and the components keep their current content.
     */
    private fun underModalProgress(
        @NlsContexts.ModalProgressTitle title: String,
        read: suspend () -> ScriptDefinitionsSnapshot,
    ): ScriptDefinitionsSnapshot? = try {
        runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.cancellable()) { read() }
    } catch (e: Throwable) {
        rethrowControlFlowException(e)
        thisLogger().error(e)
        Messages.showErrorDialog(
            project,
            KotlinBaseScriptingBundle.message("script.definitions.reload.failed"),
            KotlinBaseScriptingBundle.message("script.definitions.reload.failed.title"),
        )
        null
    }

    private fun showSnapshot(snapshot: ScriptDefinitionsSnapshot) {
        stopEditing()

        tableView.listTableModel.items = snapshot.definitions.map { it.copy() }
        tableView.visibleRowCount = snapshot.definitions.size
        tableView.tableViewModel.fireTableDataChanged()

        definitionClassesModel.replaceAll(snapshot.definitionClasses)
        classpathModel.replaceAll(snapshot.classpath)

        appliedSnapshot = snapshot.detached()
        updateReloadAvailability()
    }

    private fun currentSnapshot(): ScriptDefinitionsSnapshot = ScriptDefinitionsSnapshot(
        tableView.items.map { it.copy() },
        definitionClassesModel.items.toList(),
        classpathModel.items.toList(),
    )

    /** A row is mutable, so the kept copy must not share it with the table. */
    private fun ScriptDefinitionsSnapshot.detached(): ScriptDefinitionsSnapshot = copy(definitions = definitions.map { it.copy() })

    private fun stopEditing() {
        tableView.stopEditing()
    }

    private fun installDirtyListeners() {
        tableView.listTableModel.addTableModelListener { updateReloadAvailability() }

        val listListener = object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) = updateReloadAvailability()
            override fun intervalRemoved(e: ListDataEvent) = updateReloadAvailability()
            override fun contentsChanged(e: ListDataEvent) = updateReloadAvailability()
        }
        definitionClassesModel.addListDataListener(listListener)
        classpathModel.addListDataListener(listListener)
    }

    private fun updateReloadAvailability() {
        val clean = !isModified()
        reloadButton.isEnabled = clean
        // An enabled button needs no reason, so the text goes away with the disabled state.
        reloadButton.setToolTipText(if (clean) null else reloadDisabledTooltip)
    }

    /**
     * Keeps the editor as tall as its content, between one and [MAX_VISIBLE_LIST_ROWS] rows.
     */
    private fun bindCompactHeight(list: JBList<String>, model: CollectionListModel<String>, panel: JComponent) {
        fun refresh() {
            list.visibleRowCount = (model.size + 1).coerceIn(1, MAX_VISIBLE_LIST_ROWS)
            panel.preferredSize = null
            panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height)
            panel.revalidate()
        }

        model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) = refresh()
            override fun intervalRemoved(e: ListDataEvent) = refresh()
            override fun contentsChanged(e: ListDataEvent) = refresh()
        })
        refresh()
    }

    /**
     * A label with its hint beside it, and the editor below.
     *
     * The label keeps the normal font, and [hint] carries the small context help font. A row comment
     * would take the gaps of the spacing configuration, which serve the whole page.
     */
    private fun Panel.definitionField(
        @NlsContexts.Label labelText: String,
        @NlsContexts.Label hintText: String,
        editor: JComponent,
    ) {
        row {
            label(labelText).customize(UnscaledGaps(right = 8))
            cell(hint(hintText))
        }.customize(UnscaledGapsY(top = 16))
        row { cell(editor).align(AlignX.FILL) }.customize(UnscaledGapsY(top = 4))
    }

    /**
     * A plain text row, inset like a row of the definitions table above.
     *
     * The list DSL renderer draws a popup row. It insets the selection band and adds its own left
     * margin, so the entries no longer line up with the table.
     */
    private fun definitionListRenderer(): DefaultListCellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
            (this as JComponent).border = JBUI.Borders.empty(2, DEFINITION_ROW_LEFT_INSET)
        }
    }

    private fun hint(@NlsContexts.Label text: String): JBLabel = JBLabel(text).apply {
        font = JBFont.small()
        foreground = UIUtil.getContextHelpForeground()
    }

    private fun addDefinitionClass() {
        val fqn = askForDefinitionClass(null) ?: return
        definitionClassesModel.add(fqn)
        definitionClassesList.selectedIndex = definitionClassesModel.size - 1
    }

    private fun editDefinitionClass() {
        val index = definitionClassesList.selectedIndex
        if (index < 0) return
        val edited = askForDefinitionClass(definitionClassesModel.getElementAt(index)) ?: return
        definitionClassesModel.setElementAt(edited, index)
    }

    private fun askForDefinitionClass(initial: String?): String? = Messages.showInputDialog(
        project,
        KotlinBaseScriptingBundle.message("manual.loading.definition.classes.dialog.label"),
        KotlinBaseScriptingBundle.message("manual.loading.definition.classes.dialog.title"),
        null,
        initial,
        InputValidatorEx { input ->
            val value = input.trim()
            when {
                value.isEmpty() -> KotlinBaseScriptingBundle.message("manual.loading.definition.classes.error.blank")
                value != initial && definitionClassesModel.items.contains(value) ->
                    KotlinBaseScriptingBundle.message("manual.loading.definition.classes.error.duplicate")

                else -> null
            }
        },
    )?.trim()?.takeIf { it.isNotEmpty() }

    private inner class AddClasspathDirectoryAction : DumbAwareAction(
        KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.add.directory"),
        null,
        AllIcons.Nodes.Folder,
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            addClasspathEntries(chooseDirectories())
        }
    }

    private fun chooseJars(): Array<VirtualFile> {
        val descriptor = FileChooserDescriptor(false, false, true, true)
            .withTitle(KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.chooser.jar.title"))
        return FileChooser.chooseFiles(descriptor, project, null)
    }

    private fun chooseDirectories(): Array<VirtualFile> {
        val descriptor = FileChooserDescriptorFactory.multiDirs()
            .withTitle(KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.chooser.directory.title"))
        return FileChooser.chooseFiles(descriptor, project, null)
    }

    private fun addClasspathEntries(files: Array<VirtualFile>) {
        for (file in files) {
            val entry = VfsUtil.getLocalFile(file).presentableUrl
            if (!classpathModel.items.contains(entry)) {
                classpathModel.add(entry)
            }
        }
    }

    private fun editClasspathEntry() {
        val index = classpathList.selectedIndex
        if (index < 0) return
        val edited = Messages.showInputDialog(
            project,
            KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.dialog.label"),
            KotlinBaseScriptingBundle.message("manual.loading.definition.classpath.dialog.title"),
            null,
            classpathModel.getElementAt(index),
            null,
        )?.trim()?.takeIf { it.isNotEmpty() } ?: return
        classpathModel.setElementAt(edited, index)
    }
}
