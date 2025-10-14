// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.settings

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
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptTemplatesFromDependenciesDefinitionSource
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionPersistentSettings.ScriptDefinitionSetting
import org.jetbrains.kotlin.idea.core.script.shared.KOTLIN_SCRIPTING_SETTINGS_ID
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.shared.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal class KotlinScriptingSettingsConfigurable(val project: Project, val coroutineScope: CoroutineScope) : SearchableConfigurable {
    private val definitionsFromClassPathTitle: AtomicProperty<String> = AtomicProperty("")

    private var persistedModels = calculateModels()
    private var currentModels = persistedModels.deepCopy()

    private fun List<ScriptDefinitionModel>.deepCopy(): MutableList<ScriptDefinitionModel> = map { it.copy() }.toMutableList()

    override fun isModified(): Boolean = persistedModels != currentModels

    private fun calculateModels(): MutableList<ScriptDefinitionModel> {
        val settingsProvider = ScriptDefinitionPersistentSettings.getInstance(project)

        return ScriptDefinitionProviderImpl.getInstance(project).definitionsFromSources
            .sortedBy { settingsProvider.getScriptDefinitionOrder(it) }
            .map {
                ScriptDefinitionModel(
                    id = it.definitionId,
                    name = it.name,
                    pattern = it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.scriptFilePattern?.pattern
                        ?: (it as? ScriptDefinition.FromConfigurationsBase)?.fileNamePattern
                        ?: (it as? ScriptDefinition.FromConfigurationsBase)?.filePathPattern
                        ?: ("." + it.fileExtension),
                    canBeSwitchedOff = it.canDefinitionBeSwitchedOff,
                    isEnabled = settingsProvider.isScriptDefinitionEnabled(it)
                )
            }.toMutableList()
    }

    override fun reset() {
        if (isModified) {
            persistedModels.forEach { persisted ->
                currentModels.find { it.id == persisted.id }?.let { current ->
                    current.isEnabled = persisted.isEnabled
                }
            }

            currentModels.sortBy { current ->
                persistedModels.indexOfFirst { it.id == current.id }
            }
        }
    }

    override fun createComponent(): JComponent {
        val view = TableView(ScriptDefinitionTable(currentModels)).apply {
            visibleRowCount = 10
            showVerticalLines = false
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            setEmptyState(message("status.text.no.definitions"))
        }

        val decorator = ToolbarDecorator.createDecorator(view)
            .disableAddAction()
            .disableRemoveAction()
            .createPanel()

        return panel {
            row(message("kotlin.script.definitions.title")) {}
            row {
                cell(decorator)
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
                            project.scriptDefinitionsSourceOfType<ScriptTemplatesFromDependenciesDefinitionSource>()
                                ?.scanAndLoadDefinitions()
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
    }

    override fun apply() {
        if (isModified) {
            val settings = currentModels.map {
                ScriptDefinitionSetting(
                    it.name,
                    it.id,
                    it.isEnabled
                )
            }

            ScriptDefinitionPersistentSettings.getInstance(project).setSettings(settings)
            persistedModels = calculateModels()
        }
    }

    override fun getDisplayName(): String = message("script.name.kotlin.scripting")

    override fun getId(): String = KOTLIN_SCRIPTING_SETTINGS_ID
}
