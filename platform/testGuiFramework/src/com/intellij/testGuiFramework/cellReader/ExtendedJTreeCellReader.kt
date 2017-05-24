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

import com.intellij.ui.SimpleColoredComponent
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.core.BasicRobot
import org.fest.swing.driver.BasicJTreeCellReader
import org.fest.swing.exception.ComponentLookupException
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JTree

/**
 * @author Sergey Karashevich
 */
class ExtendedJTreeCellReader : BasicJTreeCellReader(), JTreeCellReader {

  override fun valueAt(tree: JTree, modelValue: Any?): String? {
    if (modelValue == null) return null

    val cellRendererComponent = tree.cellRenderer.getTreeCellRendererComponent(tree, modelValue, false, false, false, 0, false)
    when (cellRendererComponent) {
      is JLabel -> return cellRendererComponent.text
      is SimpleColoredComponent -> return cellRendererComponent.getText()
      else -> return cellRendererComponent.findLabel()?.text
    }
  }

  private fun SimpleColoredComponent.getText(): String?
    = this.iterator().asSequence().joinToString()

  private fun Component.findLabel(): JLabel? {
    try {
      assert(this is Container)
      val label = BasicRobot.robotWithNewAwtHierarchyWithoutScreenLock().finder().find(
        this as Container) { component -> component is JLabel }
      assert(label is JLabel)
      return label as JLabel
    }
    catch (ignored: ComponentLookupException) {
      return null
    }
  }

}
