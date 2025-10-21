// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.addOptionTag
import org.jdom.Element
import org.jetbrains.kotlin.idea.core.script.k1.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettingsStorage
import org.jetbrains.kotlin.idea.util.application.executeOnPooledThread
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

@State(
    name = "KotlinScriptingSettings",
    storages = [
      Storage("kotlinScripting.xml", deprecated = true),
      Storage(StoragePathMacros.WORKSPACE_FILE)
    ]
)
class KotlinScriptingSettingsImpl(private val project: Project) : PersistentStateComponent<Element>, KotlinScriptingSettingsStorage {
    /**
     * true if notification about multiple script definition applicable for one script file is suppressed
     */
    var suppressDefinitionsCheck = false

    var showSupportWarning = true

    var showK2SupportWarning = true

    var decideOnRemainingInSourceRootLater = false

    private var scriptDefinitions = linkedMapOf<KotlinScriptDefinitionKey, KotlinScriptDefinitionValue>()

    override fun getState(): Element {
        val definitionsRootElement = Element("KotlinScriptingSettings")

        if (suppressDefinitionsCheck) {
            definitionsRootElement.addOptionTag(
                KotlinScriptingSettingsImpl::suppressDefinitionsCheck.name,
                suppressDefinitionsCheck.toString()
            )
        }

        if (!showSupportWarning) { // only non-default value should be stored to avoid unnecessary files under .idea/ dir
            definitionsRootElement.setAttribute(SUPPORT_WARNING_ATTR, "false")
        }

        if (!showK2SupportWarning) { // only non-default value should be stored to avoid unnecessary files under .idea/ dir
            definitionsRootElement.setAttribute(K2_SUPPORT_WARNING_ATTR, "false")
        }

        if (scriptDefinitions.isEmpty()) {
            return definitionsRootElement
        }

        for (scriptDefinition in scriptDefinitions) {
            definitionsRootElement.addScriptDefinitionContentElement(scriptDefinition.key, scriptDefinition.value)
        }

        return definitionsRootElement
    }

    override fun loadState(state: Element) {
        showSupportWarning = state.getAttributeValue(SUPPORT_WARNING_ATTR)?.toBoolean() ?: true

        showK2SupportWarning = state.getAttributeValue(K2_SUPPORT_WARNING_ATTR)?.toBoolean() ?: true

        state.getOptionTag(KotlinScriptingSettingsImpl::suppressDefinitionsCheck.name)?.let {
            suppressDefinitionsCheck = it
        }

        val scriptDefinitionsList = state.getChildren(SCRIPT_DEFINITION_TAG)
        for (scriptDefinitionElement in scriptDefinitionsList) {
            scriptDefinitions[scriptDefinitionElement.toKey()] = scriptDefinitionElement.toValue()
        }

        if (scriptDefinitionsList.isNotEmpty()) {
            executeOnPooledThread {
                ScriptDefinitionsManager.getInstance(project).reorderDefinitions()
            }
        }
    }

    override fun setOrder(scriptDefinition: ScriptDefinition, order: Int) {
        scriptDefinitions[scriptDefinition.toKey()] =
            scriptDefinitions[scriptDefinition.toKey()]?.copy(order = order) ?: KotlinScriptDefinitionValue(order)
    }


    fun setEnabled(scriptDefinition: ScriptDefinition, isEnabled: Boolean) {
        scriptDefinitions[scriptDefinition.toKey()] =
            scriptDefinitions[scriptDefinition.toKey()]?.copy(isEnabled = isEnabled) ?: KotlinScriptDefinitionValue(
                scriptDefinition.order,
                isEnabled = isEnabled
            )
    }

    override fun setAutoReloadConfigurations(scriptDefinition: ScriptDefinition, autoReloadScriptDependencies: Boolean) {
        scriptDefinitions[scriptDefinition.toKey()] =
            scriptDefinitions[scriptDefinition.toKey()]?.copy(autoReloadConfigurations = autoReloadScriptDependencies)
                ?: KotlinScriptDefinitionValue(
                    scriptDefinition.order,
                    autoReloadConfigurations = autoReloadScriptDependencies
                )
    }

    override fun getScriptDefinitionOrder(scriptDefinition: ScriptDefinition): Int {
        return scriptDefinitions[scriptDefinition.toKey()]?.order ?: KotlinScriptDefinitionValue.DEFAULT.order
    }

    override fun isScriptDefinitionEnabled(scriptDefinition: ScriptDefinition): Boolean {
        return scriptDefinitions[scriptDefinition.toKey()]?.isEnabled ?: KotlinScriptDefinitionValue.DEFAULT.isEnabled
    }

    override fun autoReloadConfigurations(scriptDefinition: ScriptDefinition): Boolean {
        return scriptDefinitions[scriptDefinition.toKey()]?.autoReloadConfigurations ?: KotlinScriptDefinitionValue.DEFAULT.autoReloadConfigurations
    }

    private data class KotlinScriptDefinitionKey(
        val definitionName: String,
        val className: String
    )

    private data class KotlinScriptDefinitionValue(
        val order: Int,
        val isEnabled: Boolean = true,
        val autoReloadConfigurations: Boolean = false
    ) {
        companion object {
            val DEFAULT = KotlinScriptDefinitionValue(Integer.MAX_VALUE)
        }
    }

    private fun Element.toKey() = KotlinScriptDefinitionKey(
        getAttributeValue(KotlinScriptDefinitionKey::definitionName.name),
        getAttributeValue(KotlinScriptDefinitionKey::className.name)
    )

    private fun ScriptDefinition.toKey() =
        KotlinScriptDefinitionKey(this.name, this.definitionId)

    private fun Element.addScriptDefinitionContentElement(definition: KotlinScriptDefinitionKey, settings: KotlinScriptDefinitionValue) {
        addElement(SCRIPT_DEFINITION_TAG).apply {
            setAttribute(KotlinScriptDefinitionKey::className.name, definition.className)
            setAttribute(KotlinScriptDefinitionKey::definitionName.name, definition.definitionName)

            addElement(KotlinScriptDefinitionValue::order.name).apply {
                text = settings.order.toString()
            }

            if (!settings.isEnabled) {
                addElement(KotlinScriptDefinitionValue::isEnabled.name).apply {
                    text = "false"
                }
            }
            if (settings.autoReloadConfigurations) {
                addElement(KotlinScriptDefinitionValue::autoReloadConfigurations.name).apply {
                    text = "true"
                }
            }
        }
    }

    private fun Element.addElement(name: String): Element {
        val element = Element(name)
        addContent(element)
        return element
    }

    private fun Element.toValue(): KotlinScriptDefinitionValue {
        val order = getChildText(KotlinScriptDefinitionValue::order.name)?.toInt()
                    ?: KotlinScriptDefinitionValue.DEFAULT.order
        val isEnabled = getChildText(KotlinScriptDefinitionValue::isEnabled.name)?.toBoolean()
                        ?: KotlinScriptDefinitionValue.DEFAULT.isEnabled
        val autoReloadScriptDependencies = getChildText(KotlinScriptDefinitionValue::autoReloadConfigurations.name)?.toBoolean()
                                           ?: KotlinScriptDefinitionValue.DEFAULT.autoReloadConfigurations

        return KotlinScriptDefinitionValue(order, isEnabled, autoReloadScriptDependencies)
    }

    private fun Element.getOptionTag(name: String) =
        getChildren("option").firstOrNull { it.getAttribute("name").value == name }?.getAttributeBooleanValue("value")

    companion object {
        fun getInstance(project: Project): KotlinScriptingSettingsImpl = project.service<KotlinScriptingSettingsStorage>() as KotlinScriptingSettingsImpl

        private const val SCRIPT_DEFINITION_TAG = "scriptDefinition"
        private const val SUPPORT_WARNING_ATTR = "supportWarning"
        private const val K2_SUPPORT_WARNING_ATTR = "k2SupportWarning"
    }
}