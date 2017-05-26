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
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.driver.BasicJTreeCellReader
import org.fest.swing.exception.ComponentLookupException
import java.awt.Component
import java.awt.Container
import java.util.*
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
      else -> return cellRendererComponent.findText()
    }
  }

  private fun SimpleColoredComponent.getText(): String?
    = this.iterator().asSequence().joinToString()

  private fun Component.findText(): String? {
    try {
      assert(this is Container)
      val container = this as Container
      val resultList = ArrayList<String>()
      resultList.addAll(
        findAllWithRobot(container, JLabel::class.java)
          .filter { !it.text.isNullOrEmpty() }
          .map { it.text }
      )
      resultList.addAll(
        findAllWithRobot(container, SimpleColoredComponent::class.java)
          .filter { !it.getText().isNullOrEmpty() }
          .map { it.getText()!! }
      )
      return resultList.filter { !it.isNullOrEmpty() }.firstOrNull()
    }
    catch (ignored: ComponentLookupException) {
      return null
    }
  }

  fun <ComponentType : Component> findAllWithRobot(container: Container, clazz: Class<ComponentType>): List<ComponentType> {
    val robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
    val result = robot.finder().findAll(container, object : GenericTypeMatcher<ComponentType>(clazz) {
      override fun isMatching(component: ComponentType) = true
    })
    robot.cleanUpWithoutDisposingWindows()
    return result.toList()
  }

}
