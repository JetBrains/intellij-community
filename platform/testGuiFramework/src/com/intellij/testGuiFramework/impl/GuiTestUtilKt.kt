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
package com.intellij.testGuiFramework.impl

import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.framework.GuiTestUtil
import org.fest.swing.core.ComponentMatcher
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiQuery
import org.fest.swing.edt.GuiTask
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Component
import java.awt.Container
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JRadioButton

/**
 * @author Sergey Karashevich
 */
object GuiTestUtilKt {

  fun createTree(string: String): ImmutableTree<String> {
    val currentPath = ArrayList<String>()
    var lines = string.split("\n")
    if (lines.last().isEmpty()) lines = lines.subList(0, lines.lastIndex - 1)

    val tree: ImmutableTree<String> = ImmutableTree()
    var lastNode: ImmutableTreeNode<String>? = null

    try {
      for (line in lines) {
        if (currentPath.isEmpty()) {
          currentPath.add(line)
          tree.root = ImmutableTreeNode(line.withoutIndent(), null)
          lastNode = tree.root
        }
        else {
          if (currentPath.last() hasDiffIndentFrom line) {
            if (currentPath.last().getIndent() > line.getIndent()) {
              while (currentPath.last() hasDiffIndentFrom line) {
                currentPath.removeAt(currentPath.lastIndex)
                lastNode = lastNode!!.parent
              }
              currentPath.removeAt(currentPath.lastIndex)
              currentPath.add(line)
              lastNode = lastNode!!.parent!!.createChild(line.withoutIndent())
            }
            else {
              currentPath.add(line)
              lastNode = lastNode!!.createChild(line.withoutIndent())
            }
          }
          else {
            currentPath.removeAt(currentPath.lastIndex)
            currentPath.add(line)
            lastNode = lastNode!!.parent!!.createChild(line.withoutIndent())
          }
        }
      }

      return tree
    }
    catch(e: Exception) {
      throw Exception("Unable to build a tree from given data. Check indents and ")
    }
  }

  private infix fun String.hasDiffIndentFrom(s: String): Boolean {
    return this.getIndent() != s.getIndent()
  }

  private fun String.getIndent() = this.indexOfFirst { it != ' ' }

  private fun String.withoutIndent() = this.substring(this.getIndent())

  private operator fun String.times(n: Int): String {
    val sb = StringBuilder(n)
    for (i in 1..n) {
      sb.append(this)
    }
    return sb.toString()
  }

  class ImmutableTree<Value> {
    var root: ImmutableTreeNode<Value>? = null

    fun print() {
      if (root == null) throw Exception("Unable to print tree without root (or if root is null)")
      printRecursive(root!!, 0)
    }

    fun printRecursive(root: ImmutableTreeNode<Value>, indent: Int) {
      println(" " * indent + root.value)
      if (!root.isLeaf()) root.children.forEach { printRecursive(it, indent + 2) }
    }

  }


  data class ImmutableTreeNode<Value>(val value: Value,
                                      val parent: ImmutableTreeNode<Value>?,
                                      val children: LinkedList<ImmutableTreeNode<Value>> = LinkedList()) {

    fun createChild(childValue: Value): ImmutableTreeNode<Value> {
      val child = ImmutableTreeNode<Value>(childValue, this)
      children.add(child)
      return child
    }

    fun countChildren() = children.count()

    fun isLeaf() = (children.count() == 0)

  }

  fun Component.isTextComponent(): Boolean {
    val textComponentsTypes = arrayOf(JLabel::class.java, JRadioButton::class.java)
    return textComponentsTypes.any { it.isInstance(this) }
  }

  fun Component.getComponentText(): String? {
    when (this) {
      is JLabel -> return this.text
      is JRadioButton -> return this.text
      else -> return null
    }
  }

  fun findComponentByText(robot: Robot, container: Container, text: String): Component {
    return withPauseWhenNull { robot.finder().findAll(container, ComponentMatcher { component ->
      component!!.isShowing && component.isTextComponent() && component.getComponentText() == text }).firstOrNull() }
  }

  fun <BoundedComponent> findBoundedComponentByText(robot: Robot, container: Container, text: String, componentType: Class<BoundedComponent>): BoundedComponent {
    val componentWithText = findComponentByText(robot, container, text)
    if (componentWithText is JLabel && componentWithText.labelFor != null) {
      val labeledComponent = componentWithText.labelFor
      if (componentType.isInstance(labeledComponent)) return labeledComponent as BoundedComponent
      return robot.finder().find(labeledComponent as Container) { component -> componentType.isInstance(component) } as BoundedComponent
    }
    try {
      return withPauseWhenNull {
        val componentsOfInstance = robot.finder().findAll(container, ComponentMatcher { component -> componentType.isInstance(component) })
        componentsOfInstance.filter { it.isShowing && it.onHeightCenter(componentWithText, true) }
          .sortedBy { it.bounds.x }
          .firstOrNull()
      } as BoundedComponent
    } catch (e: WaitTimedOutError) {
      throw ComponentLookupException("Unable to find component of type: ${componentType.simpleName} in $container by text: $text" )
    }
  }

  //Does the textComponent intersects horizontal line going through the center of this component and lays lefter than this component
  fun Component.onHeightCenter(textComponent: Component, onLeft: Boolean): Boolean {
    val centerXAxis = this.bounds.height / 2 + this.locationOnScreen.y
    val sideCheck =
      if (onLeft)
        textComponent.locationOnScreen.x  < this.locationOnScreen.x
      else
        textComponent.locationOnScreen.x > this.locationOnScreen.x
    return (textComponent.locationOnScreen.y <= centerXAxis)
           && (textComponent.locationOnScreen.y + textComponent.bounds.height >= centerXAxis)
           && (sideCheck)
  }

  fun runOnEdt(task: () -> Unit) {
    GuiActionRunner.execute(object : GuiTask() {
      override fun executeInEDT() {
        task()
      }
    })
  }

  /**
   * waits for 30 sec timeout when testWithPause() not return null
   */
  fun <ReturnType> withPauseWhenNull(timeoutInSeconds: Int = 30, testWithPause: () -> ReturnType?): ReturnType {
    val ref = Ref<ReturnType>()
    Pause.pause(object: Condition("With pause...") {
      override fun test(): Boolean {
        val testWithPauseResult = testWithPause()
        if (testWithPauseResult != null) ref.set(testWithPauseResult)
        return (testWithPauseResult != null)
      }
    }, Timeout.timeout(timeoutInSeconds.toLong(), TimeUnit.SECONDS))
    return ref.get()
  }

  fun waitUntil(condition: String, timeoutInSeconds: Int = 60, conditionalFunction: () -> Boolean) {
    Pause.pause(object : Condition("Wait for $timeoutInSeconds until $condition") {
      override fun test() = conditionalFunction()
    }, Timeout.timeout(timeoutInSeconds.toLong(), TimeUnit.SECONDS))
  }

  fun <ComponentType : Component> findAllWithBFS(container: Container, clazz: Class<ComponentType>): List<ComponentType> {
    val result = LinkedList<ComponentType>()
    val queue: Queue<Component> = LinkedList()

    @Suppress("UNCHECKED_CAST")
    fun check(container: Component) {
      if (clazz.isInstance(container)) result.add(container as ComponentType)
    }

    queue.add(container)
    while(queue.isNotEmpty()) {
      val polled = queue.poll()
      check(polled)
      if (polled is Container)
        queue.addAll(polled.components)
    }

    return result

  }

  fun <ComponentType : Component?> waitUntilGone(robot: Robot, timeoutInSeconds: Int = 30, root: Container? = null, matcher: GenericTypeMatcher<ComponentType>) {
    return GuiTestUtil.waitUntilGone(robot, root, timeoutInSeconds, matcher)
  }

  fun <ComponentType : Component?> typeMatcher(componentTypeClass: Class<ComponentType>,
                                                       matcher: (ComponentType) -> Boolean): GenericTypeMatcher<ComponentType> {
    return object : GenericTypeMatcher<ComponentType>(componentTypeClass) {
      override fun isMatching(component: ComponentType): Boolean = matcher(component)
    }
  }


  fun <ReturnType> computeOnEdt(query: () -> ReturnType): ReturnType?
    = GuiActionRunner.execute(object : GuiQuery<ReturnType>() {
    override fun executeInEDT(): ReturnType = query()
  })

  fun <ReturnType> computeOnEdtWithTry(query: () -> ReturnType?): ReturnType? {
    val result = GuiActionRunner.execute(object : GuiQuery<Pair<ReturnType?, Throwable?>>() {
      override fun executeInEDT(): kotlin.Pair<ReturnType?, Throwable?> {
        try {
          return Pair(query(), null)
        }
        catch (e: Exception) {
          return Pair(null, e)
        }
      }
    })
    if (result?.second != null) throw result.second!!
    return result?.first
  }

}

fun main(args: Array<String>) {
  val tree = GuiTestUtilKt.createTree("project\n" +
                                      "  src\n" +
                                      "    com.username\n" +
                                      "      Test1.java\n" +
                                      "      Test2.java\n" +
                                      "  lib\n" +
                                      "    someLib1\n" +
                                      "   someLib2")
  tree.print()
}
