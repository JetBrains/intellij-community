// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.setEmptyState
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.EditorNotifications
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle.message
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptTemplatesFromDependenciesDefinitionSource
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionPersistentSettings.ScriptDefinitionSetting
import org.jetbrains.kotlin.idea.core.script.shared.KOTLIN_SCRIPTING_SETTINGS_ID
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.shared.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

internal class KotlinScriptingSettingsConfigurable(val project: Project, val coroutineScope: CoroutineScope) : SearchableConfigurable {

    private var model = calculateModel()
    private val definitionsFromClassPathTitle: AtomicProperty<String> = AtomicProperty("")

    private fun calculateModel(): KotlinScriptDefinitionsModel {
        val settingsProvider = ScriptDefinitionPersistentSettings.getInstance(project)
        val definitions = project.service<ScriptDefinitionProvider>().currentDefinitions
            .sortedBy { settingsProvider.getScriptDefinitionOrder(it) }
            .map {
                ScriptDefinitionModel(
                    it,
                    settingsProvider.isScriptDefinitionEnabled(it)
                )
            }.toMutableList()

        return KotlinScriptDefinitionsModel(definitions)
    }

    override fun createComponent(): JComponent = panel {
        row(message("kotlin.script.definitions.title")) {}
        row {
            cell(getDefinitionsTable())
                .align(Align.FILL)
            rowComment(message("text.first.definition.that.matches.script.pattern.extension.applied.starting.from.top"))
        }

        row {
            button(KotlinBaseScriptingBundle.message("button.scan.classpath")) {
                coroutineScope.launch {
                    val definitionsFromClassPath = withBackgroundProgress(
                        project,
                        title = KotlinBaseScriptingBundle.message("looking.for.script.definitions.in.classpath")
                    ) {
                        project.scriptDefinitionsSourceOfType<ScriptTemplatesFromDependenciesDefinitionSource>()?.scanAndLoadDefinitions()
                    } ?: emptyList()

                    if (definitionsFromClassPath.isEmpty()) {
                        definitionsFromClassPathTitle.set(KotlinBaseScriptingBundle.message("label.kotlin.script.no.definitions.found"))
                    } else {
                        definitionsFromClassPathTitle.set(
                            KotlinBaseScriptingBundle.message(
                                "label.kotlin.script.definitions.found",
                                definitionsFromClassPath.size
                            )
                        )
                    }
                    enabled(false)
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
            label("").bindText(definitionsFromClassPathTitle)
        }
    }

    private fun getDefinitionsTable(): JPanel {
        val table = TableView(model).apply {
            visibleRowCount = 10
            showVerticalLines = false
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            setEmptyState(message("status.text.no.definitions"))
        }

        return ToolbarDecorator.createDecorator(table)
            .disableAddAction()
            .disableRemoveAction()
            .createPanel()
    }

    override fun isModified(): Boolean = isScriptDefinitionsChanged()

    override fun apply() {
        if (isScriptDefinitionsChanged()) {
            val settings = model.items.map {
                ScriptDefinitionSetting(
                    it.definition.name,
                    it.definition.definitionId,
                    it.isEnabled
                )
            }

            ScriptDefinitionPersistentSettings.getInstance(project).setSettings(settings)
            model = calculateModel()
        }
    }

    private fun isScriptDefinitionsChanged(): Boolean {
        val settings = ScriptDefinitionPersistentSettings.getInstance(project).state.settings

        if (model.items.size != settings.size) {
            return true
        }

        for (i in 0..<model.items.size) {
            val setting = settings[i]
            val modelItem = model.items[i]

            if (setting.definitionId != modelItem.definition.definitionId
                || setting.name != modelItem.definition.name
                || setting.enabled != modelItem.isEnabled
            ) {
                return true
            }
        }

        return false
    }

    override fun getDisplayName(): String = message("script.name.kotlin.scripting")

    override fun getId(): String = KOTLIN_SCRIPTING_SETTINGS_ID
}
