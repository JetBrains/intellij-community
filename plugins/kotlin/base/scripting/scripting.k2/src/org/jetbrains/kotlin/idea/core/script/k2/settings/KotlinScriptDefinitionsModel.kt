// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import javax.swing.JTable

data class ScriptDefinitionModel(
    val id: String,
    val name: String,
    val pattern: String,
    val canBeSwitchedOff: Boolean,
    var isEnabled: Boolean,
)

class ScriptDefinitionTable(definitions: MutableList<ScriptDefinitionModel>) : ListTableModel<ScriptDefinitionModel>(
    arrayOf(
        ScriptDefinitionName(),
        ScriptDefinitionPattern(),
        ScriptDefinitionIsEnabled(),
    ), definitions, 0
) {

    private class ScriptDefinitionName : ColumnInfo<ScriptDefinitionModel, String>(
        KotlinBundle.message("kotlin.script.definitions.model.name.name")
    ) {
        override fun valueOf(item: ScriptDefinitionModel) = item.name
    }

    private class ScriptDefinitionPattern : ColumnInfo<ScriptDefinitionModel, String>(
        KotlinBundle.message("kotlin.script.definitions.model.name.pattern.extension")
    ) {
        override fun valueOf(item: ScriptDefinitionModel): String = item.pattern
    }

    private class ScriptDefinitionIsEnabled :
        ColumnInfo<ScriptDefinitionModel, Boolean>(KotlinBundle.message("kotlin.script.definitions.model.name.is.enabled")) {
        override fun getEditor(item: ScriptDefinitionModel?) = BooleanTableCellEditor()
        override fun getRenderer(item: ScriptDefinitionModel?) = BooleanTableCellRenderer()
        override fun getWidth(table: JTable?): Int = 90

        override fun valueOf(item: ScriptDefinitionModel): Boolean = item.isEnabled
        override fun setValue(item: ScriptDefinitionModel, value: Boolean) {
            item.isEnabled = value
        }

        override fun isCellEditable(item: ScriptDefinitionModel): Boolean = item.canBeSwitchedOff
    }
}
