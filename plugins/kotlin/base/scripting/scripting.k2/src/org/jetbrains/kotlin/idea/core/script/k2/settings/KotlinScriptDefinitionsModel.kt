// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.settings

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

internal data class ScriptDefinitionTableModel(
    val id: String,
    val name: String,
    val pattern: String,
    val canBeSwitchedOff: Boolean,
    var isEnabled: Boolean,
)

internal class ScriptDefinitionTable(definitions: MutableList<ScriptDefinitionTableModel>) : ListTableModel<ScriptDefinitionTableModel>(
    arrayOf(
        ScriptDefinitionName(),
        ScriptDefinitionPattern(),
        ScriptDefinitionIsEnabled(),
    ), definitions, 0
) {

    private abstract class ScriptDefinitionTableColumnInfo<T>(
        @NlsContexts.ColumnName name: String
    ) : ColumnInfo<ScriptDefinitionTableModel, T>(name) {
        override fun getRenderer(item: ScriptDefinitionTableModel?): TableCellRenderer {
            return object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (component is JComponent) {
                        component.border = JBUI.Borders.emptyLeft(10)
                    }
                    return component
                }
            }
        }
    }

    private class ScriptDefinitionName : ScriptDefinitionTableColumnInfo<String>(
        KotlinBundle.message("kotlin.script.definitions.model.name.name")
    ) {
        override fun valueOf(item: ScriptDefinitionTableModel) = item.name
    }

    private class ScriptDefinitionPattern : ScriptDefinitionTableColumnInfo<String>(
        KotlinBundle.message("kotlin.script.definitions.model.name.pattern.extension")
    ) {
        override fun valueOf(item: ScriptDefinitionTableModel): String = item.pattern
    }

    private class ScriptDefinitionIsEnabled :
        ScriptDefinitionTableColumnInfo<Boolean>(KotlinBundle.message("kotlin.script.definitions.model.name.is.enabled")) {
        override fun getEditor(item: ScriptDefinitionTableModel?) = BooleanTableCellEditor()
        override fun getRenderer(item: ScriptDefinitionTableModel?) = BooleanTableCellRenderer()
        override fun getWidth(table: JTable?): Int = 90

        override fun valueOf(item: ScriptDefinitionTableModel): Boolean = item.isEnabled
        override fun setValue(item: ScriptDefinitionTableModel, value: Boolean) {
            item.isEnabled = value
        }

        override fun isCellEditable(item: ScriptDefinitionTableModel): Boolean = item.canBeSwitchedOff
    }
}
