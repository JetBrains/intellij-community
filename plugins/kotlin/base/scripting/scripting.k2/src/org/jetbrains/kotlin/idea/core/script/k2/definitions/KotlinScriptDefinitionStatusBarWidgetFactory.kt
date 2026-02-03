// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import java.awt.Component
import javax.swing.JLabel

const val ID: String = "kotlin.script.definition"

class KotlinScriptDefinitionStatusBarWidgetFactory : StatusBarWidgetFactory {
    private lateinit var component: JLabel

    override fun getId(): @NonNls String = ID

    override fun getDisplayName(): @NlsContexts.ConfigurableName String = DISPLAY_NAME

    override fun isAvailable(project: Project): Boolean = getSelectedEditorDefinition(project) != null

    override fun isEnabledByDefault(): Boolean = Registry.`is`("kotlin.scripting.show.widget", false)

    override fun createWidget(project: Project): StatusBarWidget {
        component = JLabel().apply {
            toolTipText = KotlinBaseScriptingBundle.message("tooltip.this.definition.used.for.current.kotlin.script.configuration")
            text = getSelectedEditorDefinition(project)?.name
            alignmentX = Component.LEFT_ALIGNMENT
            icon = KotlinIcons.SCRIPT
        }

        return Widget(component)
    }

    fun updateWidgetText(project: Project) {
        component.text = getSelectedEditorDefinition(project)?.name
    }
}

private fun getSelectedEditorDefinition(project: Project): ScriptDefinition? =
    FileEditorManager.getInstance(project).getSelectedEditor()?.file?.findScriptDefinition(project)

class KotlinScriptDefinitionStatusBarWidgetListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val manager = event.manager.project.service<StatusBarWidgetsManager>()
        (manager.findWidgetFactory(ID) as? KotlinScriptDefinitionStatusBarWidgetFactory)?.updateWidgetText(event.manager.project)
        manager.updateWidget(KotlinScriptDefinitionStatusBarWidgetFactory::class.java)
    }
}

@NlsSafe
private const val DISPLAY_NAME = "Kotlin Script definition"

private class Widget(private val component: JLabel) : CustomStatusBarWidget {
    override fun ID(): String = ID
    override fun getComponent(): JLabel = component
}