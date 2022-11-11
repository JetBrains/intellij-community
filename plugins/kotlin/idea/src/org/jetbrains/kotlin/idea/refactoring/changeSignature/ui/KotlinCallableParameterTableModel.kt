// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.ui

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.util.ui.ColumnInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
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