// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.script.k2

import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.BundledIdeScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import javax.swing.JTable

class ModelDescriptor(
    val definition: ScriptDefinition,
    var isEnabled: Boolean,
)

class KotlinScriptDefinitionsModel(definitions: MutableList<ModelDescriptor>) :
    ListTableModel<ModelDescriptor>(
        arrayOf(
            ScriptDefinitionName(),
            ScriptDefinitionPattern(),
            ScriptDefinitionIsEnabled(),
        ),
        definitions,
        0
    ) {

    private class ScriptDefinitionName : ColumnInfo<ModelDescriptor, String>(
        KotlinBundle.message("kotlin.script.definitions.model.name.name")
    ) {
        override fun valueOf(item: ModelDescriptor) = item.definition.name
    }

    private class ScriptDefinitionPattern : ColumnInfo<ModelDescriptor, String>(
        KotlinBundle.message("kotlin.script.definitions.model.name.pattern.extension")
    ) {
        override fun valueOf(item: ModelDescriptor): String {
            val definition = item.definition
            return definition.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.scriptFilePattern?.pattern
                ?: (definition as? ScriptDefinition.FromConfigurationsBase)?.fileNamePattern
                ?: ("." + definition.fileExtension)
        }
    }

    private class ScriptDefinitionIsEnabled : BooleanColumn(
        KotlinBundle.message("kotlin.script.definitions.model.name.is.enabled")
    ) {
        override fun valueOf(item: ModelDescriptor): Boolean = item.isEnabled
        override fun setValue(item: ModelDescriptor, value: Boolean) {
            item.isEnabled = value
        }

        override fun isCellEditable(item: ModelDescriptor): Boolean {
            return item.definition.asLegacyOrNull<BundledIdeScriptDefinition>() == null
                    && item.definition.canDefinitionBeSwitchedOff
        }
    }
}

private abstract class BooleanColumn(@Nls message: String) : ColumnInfo<ModelDescriptor, Boolean>(message) {
    override fun getEditor(item: ModelDescriptor?) = BooleanTableCellEditor()
    override fun getRenderer(item: ModelDescriptor?) = BooleanTableCellRenderer()
    override fun getWidth(table: JTable?): Int = 90
}