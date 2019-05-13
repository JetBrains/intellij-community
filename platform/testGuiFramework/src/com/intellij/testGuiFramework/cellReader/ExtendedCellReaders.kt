/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.cellReader

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.computeOnEdt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.findAllWithBFS
import com.intellij.ui.MultilineTreeCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import org.fest.swing.cell.JComboBoxCellReader
import org.fest.swing.cell.JListCellReader
import org.fest.swing.cell.JTableCellReader
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.driver.BasicJComboBoxCellReader
import org.fest.swing.driver.BasicJListCellReader
import org.fest.swing.driver.BasicJTableCellReader
import org.fest.swing.driver.BasicJTreeCellReader
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiQuery
import org.fest.swing.exception.ComponentLookupException
import java.awt.Component
import java.awt.Container
import java.lang.Error
import java.util.*
import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode


/**
 * @author Sergey Karashevich
 */
class ExtendedJTreeCellReader : BasicJTreeCellReader(), JTreeCellReader {

  override fun valueAt(tree: JTree, modelValue: Any?): String? = valueAtExtended(tree, modelValue, false)

  fun valueAtExtended(tree: JTree, modelValue: Any?, isExtended: Boolean = true): String? {
    if (modelValue == null) return null
    val isLeaf: Boolean = try {
      modelValue is DefaultMutableTreeNode && modelValue.isLeaf
    }
    catch (e: Error) {
      false
    }
    return computeOnEdt {
      val cellRendererComponent = tree.cellRenderer.getTreeCellRendererComponent(tree, modelValue, false, false, isLeaf, 0, false)
      getValueWithCellRenderer(cellRendererComponent, isExtended)
    }
  }
}

class ExtendedJListCellReader : BasicJListCellReader(), JListCellReader {

  @Nullable
  override fun valueAt(@Nonnull list: JList<*>, index: Int): String? {
    val element = list.model.getElementAt(index) ?: return null
    val cellRendererComponent = GuiTestUtil.getListCellRendererComponent(list, element, index)
    return getValueWithCellRenderer(cellRendererComponent)
  }
}

class ExtendedJTableCellReader : BasicJTableCellReader(), JTableCellReader {

  override fun valueAt(table: JTable, row: Int, column: Int): String? {
    val cellRendererComponent = table.prepareRenderer(table.getCellRenderer(row, column), row, column)
    return super.valueAt(table, row, column) ?: getValueWithCellRenderer(cellRendererComponent)
  }
}

class ExtendedJComboboxCellReader : BasicJComboBoxCellReader(), JComboBoxCellReader {

  private val REFERENCE_JLIST = newJList()

  override fun valueAt(comboBox: JComboBox<*>, index: Int): String? {
    val item: Any? = comboBox.getItemAt(index)
    val listCellRenderer: ListCellRenderer<Any?> = comboBox.renderer as ListCellRenderer<Any?>
    val cellRendererComponent = listCellRenderer.getListCellRendererComponent(REFERENCE_JLIST, item, index, true, true)
    return getValueWithCellRenderer(cellRendererComponent)
  }

  @Nonnull
  private fun newJList(): JList<out Any?> {
    return GuiActionRunner.execute(object : GuiQuery<JList<out Any?>>() {
      override fun executeInEDT(): JList<out Any?> = JBList()
    })!!
  }
}

private fun getValueWithCellRenderer(cellRendererComponent: Component, isExtended: Boolean = true): String? {
  val result = when (cellRendererComponent) {
    is JLabel -> cellRendererComponent.text
    is NodeRenderer -> {
      if (isExtended) cellRendererComponent.getFullText()
      else cellRendererComponent.getFirstText()
    } //should stands before SimpleColoredComponent because it is more specific
    is SimpleColoredComponent -> cellRendererComponent.getFullText()
    is MultilineTreeCellRenderer -> cellRendererComponent.text
    else -> cellRendererComponent.findText()
  }
  return result?.trimEnd()
}


private fun SimpleColoredComponent.getFullText(): String? {
  return computeOnEdt {
    this.getCharSequence(false).toString()
  }
}


private fun SimpleColoredComponent.getFirstText(): String? {
  return computeOnEdt {
    this.getCharSequence(true).toString()
  }
}


private fun Component.findText(): String? {
  try {
    assert(this is Container)
    val container = this as Container
    val resultList = ArrayList<String>()
    resultList.addAll(
      findAllWithBFS(container, JLabel::class.java)
        .asSequence()
        .filter { !it.text.isNullOrEmpty() }
        .map { it.text }
        .toList()
    )
    resultList.addAll(
      findAllWithBFS(container, SimpleColoredComponent::class.java)
        .asSequence()
        .filter {
          !it.getFullText().isNullOrEmpty()
        }
        .map {
          it.getFullText()!!
        }
        .toList()
    )
    return resultList.firstOrNull { !it.isEmpty() }
  }
  catch (ignored: ComponentLookupException) {
    return null
  }
}


