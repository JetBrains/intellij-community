// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class KotlinPrimaryConstructorParameterTableModel(
    methodDescriptor: KotlinMethodDescriptor,
    defaultValueContext: PsiElement
) : KotlinCallableParameterTableModel(
    methodDescriptor,
    defaultValueContext,
    ValVarColumn(),
    NameColumn<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(defaultValueContext.project),
    TypeColumn<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(defaultValueContext.project, KotlinFileType.INSTANCE),
    DefaultValueColumn<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(
        defaultValueContext.project,
        KotlinFileType.INSTANCE,
    ),
    DefaultParameterColumn(),
) {
    private class ValVarColumn : ColumnInfoBase<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>, KotlinValVar>(
        KotlinBundle.message("column.name.val.var")
    ) {
        override fun isCellEditable(item: ParameterTableModelItemBase<KotlinParameterInfo>): Boolean {
            return !item.isEllipsisType && item.parameter.isNewParameter
        }

        override fun valueOf(item: ParameterTableModelItemBase<KotlinParameterInfo>): KotlinValVar = item.parameter.valOrVar

        override fun setValue(item: ParameterTableModelItemBase<KotlinParameterInfo>, value: KotlinValVar) {
            item.parameter.valOrVar = value
        }

        override fun doCreateRenderer(item: ParameterTableModelItemBase<KotlinParameterInfo>): TableCellRenderer {
            return ComboBoxTableRenderer(KotlinValVar.values())
        }

        override fun doCreateEditor(item: ParameterTableModelItemBase<KotlinParameterInfo>): TableCellEditor {
            return DefaultCellEditor(JComboBox<ParameterTableModelItemBase<KotlinParameterInfo>>())
        }

        override fun getWidth(table: JTable): Int = table.getFontMetrics(table.font).stringWidth(name) + 8
    }

    companion object {
        fun isValVarColumn(column: ColumnInfo<*, *>?): Boolean = column is ValVarColumn
    }
}