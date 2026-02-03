// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.setEmptyState
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle.message
import org.jetbrains.kotlin.idea.configuration.KOTLIN_SCRIPTING_SETTINGS_ID
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptTemplatesFromDependenciesDefinitionSource
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.shared.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.awt.Font
import java.util.Objects.hash
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer

internal class KotlinScriptingSettingsConfigurable(val project: Project, val coroutineScope: CoroutineScope) : SearchableConfigurable {
    private val definitionsFromClassPathTitle = AtomicProperty("")
    private val explicitTemplateClassNamesProperty = AtomicProperty("")
    private val explicitTemplateClasspathProperty = AtomicProperty("")

    private var previousModificationStamp = 0
    private val currentModificationStamp: Int
        get() = hash(explicitTemplateClassNamesProperty.get(), explicitTemplateClasspathProperty.get(), currentModels)

    private val tableView = TableView(ScriptDefinitionTable(mutableListOf())).apply {
        showVerticalLines = false
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setEmptyState(message("status.text.no.definitions"))

        val defaultRenderer = tableHeader.defaultRenderer
        tableHeader.defaultRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
            defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
                background = UIUtil.getPanelBackground().brighter()
                font = component.font.deriveFont(Font.BOLD)
            }
        }
    }

    private val currentModels: List<ScriptDefinitionTableModel>
        get() = tableView.items.toList()

    override fun isModified(): Boolean = previousModificationStamp != currentModificationStamp

    private fun initFromPersistedState() {
        val state = ScriptDefinitionSettingsPersistentStateComponent.getInstance(project).state.copy()

        val models = ScriptDefinitionProviderImpl.getInstance(project).definitionsFromSources.sortedBy {
            state.getScriptDefinitionOrder(it)
        }.map {
            ScriptDefinitionTableModel(
                id = it.definitionId,
                name = it.name,
                pattern = (it as? ScriptDefinition.FromConfigurationsBase)?.fileNamePattern
                    ?: (it as? ScriptDefinition.FromConfigurationsBase)?.filePathPattern ?: ("." + it.fileExtension),
                canBeSwitchedOff = it.canDefinitionBeSwitchedOff,
                isEnabled = state.isScriptDefinitionEnabled(it)
            )
        }

        tableView.stopEditing()
        tableView.listTableModel.setItems(models)
        tableView.visibleRowCount = models.size + 1
        tableView.tableViewModel.fireTableDataChanged()

        explicitTemplateClassNamesProperty.set(state.explicitTemplateClassNames)
        explicitTemplateClasspathProperty.set(state.explicitTemplateClasspath)

        previousModificationStamp = currentModificationStamp
    }

    override fun reset() {
        initFromPersistedState()
    }

    override fun createComponent(): JComponent {
        initFromPersistedState()

        return panel {
            group(message("kotlin.script.definitions.title"), false) {
                row {
                    cell(ToolbarDecorator.createDecorator(tableView).disableAddAction().disableRemoveAction().createPanel()).align(Align.FILL)
                    rowComment(message("text.first.definition.that.matches.script.pattern.extension.applied.starting.from.top"))
                }
            }

            collapsibleGroup(KotlinBaseScriptingBundle.message("border.title.custom.scripting.loading"), false) {
                val labelGroup = "custom-definition-labels"

                row {
                    label(KotlinBaseScriptingBundle.message("script.definition.template.classes.to.load.explicitly")).widthGroup(labelGroup)
                    textField().align(AlignX.FILL).bindText(explicitTemplateClassNamesProperty).applyToComponent {
                        emptyText.text = "com.example.MyDefinition"
                    }
                }

                row {
                    label(KotlinBaseScriptingBundle.message("classpath.required.for.loading.script.definition.template.classes")).widthGroup(
                        labelGroup
                    )
                    textField().align(AlignX.FILL).bindText(explicitTemplateClasspathProperty)
                        .applyToComponent { emptyText.text = "/path/to/script-definition.jar" }
                }

                row {
                    button(KotlinBaseScriptingBundle.message("button.search.definitions")) {
                        coroutineScope.launch {
                            searchDefinitions()
                        }
                    }
                }

                row {
                    label("").bindText(definitionsFromClassPathTitle)
                    rowComment(KotlinBaseScriptingBundle.message("text.searches.project.classpath.for.known.script.definition.classes.or.marker.files"))
                }
            }
        }
    }

    private suspend fun searchDefinitions() {
        val explicitTemplateClassNames = explicitTemplateClassNamesProperty.get()
        val explicitTemplateClasspath = explicitTemplateClasspathProperty.get()

        val templateClassNames: List<String> = explicitTemplateClassNames.split(*delimeters).map { it.trim() }.filter { it.isNotEmpty() }
        val classpath: List<String> = explicitTemplateClasspath.split(*delimeters).map { it.trim() }.filter { it.isNotEmpty() }

        val definitionsFromClassPath = withBackgroundProgress(
            project, title = KotlinBaseScriptingBundle.message("looking.for.script.definitions.in.classpath")
        ) {
            project.scriptDefinitionsSourceOfType<ScriptTemplatesFromDependenciesDefinitionSource>()
                ?.searchForDefinitions(templateClassNames, classpath)
        } ?: emptyList()

        definitionsFromClassPathTitle.set(
            definitionsFromClassPath.joinToString(
                prefix = "<html>${
                    KotlinBaseScriptingBundle.message(
                        "label.kotlin.script.one.definitions.found", definitionsFromClassPath.size
                    )
                }<br>", separator = "<br>", postfix = "</html>"
            ) {
                "<code>${it.baseClassType.typeName}</code>"
            })

        ScriptDefinitionSettingsPersistentStateComponent.getInstance(project).updateSettings {
            it.copy(
                explicitTemplateClassNames = explicitTemplateClassNames, explicitTemplateClasspath = explicitTemplateClasspath
            )
        }

        val newModels = (definitionsFromClassPath.map {
            ScriptDefinitionTableModel(
                id = it.definitionId,
                name = it.name,
                pattern = (it as? ScriptDefinition.FromConfigurationsBase)?.fileNamePattern
                    ?: (it as? ScriptDefinition.FromConfigurationsBase)?.filePathPattern
                    ?: ("." + it.fileExtension),
                canBeSwitchedOff = it.canDefinitionBeSwitchedOff,
                isEnabled = true
            )
        } + currentModels).distinct()

        tableView.stopEditing()
        tableView.listTableModel.setItems(newModels)
        tableView.visibleRowCount = newModels.size + 1
        tableView.tableViewModel.fireTableDataChanged()
    }

    override fun apply() {
        if (isModified) {
            ScriptDefinitionSettingsPersistentStateComponent.getInstance(project).updateSettings {
                ScriptDefinitionSettingsPersistentStateComponent.State(
                    currentModels.map {
                        ScriptDefinitionSettingsPersistentStateComponent.DefinitionSetting(it.name, it.id, it.isEnabled)
                    },
                    explicitTemplateClassNamesProperty.get(),
                    explicitTemplateClasspathProperty.get(),
                )
            }

            previousModificationStamp = currentModificationStamp
        }
    }

    override fun getDisplayName(): String = message("script.name.kotlin.scripting")

    override fun getId(): String = KOTLIN_SCRIPTING_SETTINGS_ID

    private val delimeters = arrayOf(":", ",", ";", "\n", "\t", " ")
}
