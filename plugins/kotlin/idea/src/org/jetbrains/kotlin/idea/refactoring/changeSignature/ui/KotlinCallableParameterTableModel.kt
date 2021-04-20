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

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.render
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

abstract class KotlinCallableParameterTableModel protected constructor(
    private val methodDescriptor: KotlinMethodDescriptor,
    defaultValueContext: PsiElement,
    vararg columnInfos: ColumnInfo<*, *>?
) : ParameterTableModelBase<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>>(
    getTypeCodeFragmentContext(methodDescriptor.baseDeclaration),
    defaultValueContext,
    *columnInfos,
) {
    private val project: Project = defaultValueContext.project

    open var receiver: KotlinParameterInfo? = null

    override fun createRowItem(parameterInfo: KotlinParameterInfo?): ParameterTableModelItemBase<KotlinParameterInfo> {
        val resultParameterInfo = parameterInfo ?: KotlinParameterInfo(
            callableDescriptor = methodDescriptor.baseDescriptor,
            name = "",
        )

        val psiFactory = KtPsiFactory(project)
        val paramTypeCodeFragment: PsiCodeFragment = psiFactory.createTypeCodeFragment(
            resultParameterInfo.currentTypeInfo.render(),
            myTypeContext,
        )

        val defaultValueCodeFragment: PsiCodeFragment = psiFactory.createExpressionCodeFragment(
            resultParameterInfo.defaultValueForCall?.text ?: "",
            myDefaultValueContext,
        )

        return object : ParameterTableModelItemBase<KotlinParameterInfo>(
            resultParameterInfo,
            paramTypeCodeFragment,
            defaultValueCodeFragment,
        ) {
            override fun isEllipsisType(): Boolean = false
        }
    }

    protected class DefaultParameterColumn :
        ColumnInfoBase<KotlinParameterInfo, ParameterTableModelItemBase<KotlinParameterInfo>, Boolean>(KotlinBundle.message("column.name.default.parameter")) {
        override fun isCellEditable(pParameterTableModelItemBase: ParameterTableModelItemBase<KotlinParameterInfo>): Boolean = true

        public override fun doCreateRenderer(item: ParameterTableModelItemBase<KotlinParameterInfo>): TableCellRenderer =
            BooleanTableCellRenderer()

        public override fun doCreateEditor(o: ParameterTableModelItemBase<KotlinParameterInfo>): TableCellEditor =
            BooleanTableCellEditor()

        override fun valueOf(item: ParameterTableModelItemBase<KotlinParameterInfo>): Boolean =
            item.parameter.defaultValueAsDefaultParameter

        override fun setValue(item: ParameterTableModelItemBase<KotlinParameterInfo>, value: Boolean?) {
            item.parameter.defaultValueAsDefaultParameter = value == true
        }
    }

    companion object {
        fun isTypeColumn(column: ColumnInfo<*, *>?): Boolean = column is TypeColumn<*, *>

        fun isNameColumn(column: ColumnInfo<*, *>?): Boolean = column is NameColumn<*, *>

        fun isDefaultValueColumn(column: ColumnInfo<*, *>?): Boolean = column is DefaultValueColumn<*, *>

        fun isDefaultParameterColumn(column: ColumnInfo<*, *>?): Boolean = column is DefaultParameterColumn

        fun getTypeCodeFragmentContext(startFrom: PsiElement): KtElement = startFrom.parentsWithSelf.mapNotNull {
            when {
                it is KtNamedFunction -> it.bodyExpression ?: it.valueParameterList
                it is KtPropertyAccessor -> it.bodyExpression
                it is KtDeclaration && KtPsiUtil.isLocal(it) -> null
                it is KtConstructor<*> -> it
                it is KtClassOrObject -> it
                it is KtFile -> it
                else -> null
            }
        }.first()
    }

}