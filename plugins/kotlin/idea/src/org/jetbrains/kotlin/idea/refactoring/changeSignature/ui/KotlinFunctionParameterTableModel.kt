/*
 * Copyright 2010-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.KotlinBundle
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