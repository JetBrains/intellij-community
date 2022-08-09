// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class KotlinFunctionParameterTableModel(
    methodDescriptor: KotlinMethodDescriptor,
    defaultValueContext: PsiElement
) : KotlinCallableParameterTableModel(
    methodDescriptor,
    defaultValueContext,
    NameColumn<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(defaultValueContext.project),
    TypeColumn<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(defaultValueContext.project, KotlinFileType.INSTANCE),
    DefaultValueColumn<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(
        defaultValueContext.project,
        KotlinFileType.INSTANCE
    ),
    DefaultParameterColumn(),
    ReceiverColumn(methodDescriptor),
) {
    override fun removeRow(idx: Int) {
        if (getRowValue(idx).parameter == receiver) {
            receiver = null
        }

        super.removeRow(idx)
    }

    override var receiver: KotlinParameterInfo?
        get() = (columnInfos[columnCount - 1] as ReceiverColumn).receiver
        set(receiver) {
            (columnInfos[columnCount - 1] as ReceiverColumn).receiver = receiver
        }

    private class ReceiverColumn(methodDescriptor: KotlinMethodDescriptor) :
        ColumnInfoBase<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>, Boolean>(KotlinBundle.message("column.name.receiver")) {
        var receiver: KotlinParameterInfo? = methodDescriptor.receiver
        override fun valueOf(item: ParameterTableModelItemBase<KotlinParameterInfo>): Boolean = item.parameter == receiver

        override fun setValue(item: ParameterTableModelItemBase<KotlinParameterInfo>, value: Boolean?) {
            if (value == null) return
            receiver = if (value) item.parameter else null
        }

        override fun isCellEditable(pParameterTableModelItemBase: ParameterTableModelItemBase<KotlinParameterInfo>): Boolean = true

        public override fun doCreateRenderer(item: ParameterTableModelItemBase<KotlinParameterInfo>): TableCellRenderer =
            BooleanTableCellRenderer()

        public override fun doCreateEditor(o: ParameterTableModelItemBase<KotlinParameterInfo>): TableCellEditor =
            BooleanTableCellEditor()
    }

    companion object {
        fun isReceiverColumn(column: ColumnInfo<*, *>?): Boolean = column is ReceiverColumn
    }
}