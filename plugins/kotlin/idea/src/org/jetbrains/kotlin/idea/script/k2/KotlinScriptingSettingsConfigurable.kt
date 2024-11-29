// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.setEmptyState
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.ui.EditorNotifications
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle.message
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDefinitionPersistentSettings
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDefinitionSetting
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.idea.script.configuration.ScriptingSupportSpecificSettingsProvider
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class KotlinScriptingSettingsConfigurable(val project: Project, val coroutineScope: CoroutineScope) : SearchableConfigurable {
    companion object {
        const val ID: String = "preferences.language.Kotlin.scripting"
    }

    private var model = calculateModel()
    private val definitionsFromClassPathTitle: AtomicProperty<String> = AtomicProperty("")

    private fun calculateModel(): KotlinScriptDefinitionsModel {
        val settingsByDefinitionId = ScriptDefinitionPersistentSettings.getInstance(project)
            .getIndexedSettingsPerDefinition()

        val definitions = K2ScriptDefinitionProvider.getInstance(project).getAllDefinitions()
            .sortedBy { settingsByDefinitionId[it.definitionId]?.index ?: it.order }
            .map {
                DefinitionModelDescriptor(
                    it,
                    settingsByDefinitionId[it.definitionId]?.setting?.enabled != false
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
                    val definitionsFromClassPath = withBackgroundProgress(project, title = KotlinBaseScriptingBundle.message("looking.for.script.definitions.in.classpath")) {
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

        for (provider in ScriptingSupportSpecificSettingsProvider.SETTINGS_PROVIDERS.getExtensionList(project)) {
            group(indent = false) {
                row(provider.title) {}
                row {
                    provider.createConfigurable().createComponent()?.let {
                        cell(it)
                            .align(Align.FILL)
                    }
                }
            }
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
            val settings = model.items.mapIndexed { index, item ->
                ScriptDefinitionSetting(
                    item.definition.definitionId,
                    item.isEnabled
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
                || setting.enabled != modelItem.isEnabled
            ) {
                return true
            }
        }

        return false
    }

    override fun getDisplayName(): String = message("script.name.kotlin.scripting")

    override fun getId(): String = ID
}