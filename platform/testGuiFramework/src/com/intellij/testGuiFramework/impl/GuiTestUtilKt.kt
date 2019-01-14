// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import com.intellij.diagnostic.MessagePool
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.framework.toPrintable
import com.intellij.testGuiFramework.impl.GuiRobotHolder.robot
import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.EngravedLabel
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
import org.fest.swing.timing.Wait
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JRadioButton
import kotlin.collections.ArrayList

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
    catch (e: Exception) {
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

    fun countChildren(): Int = children.count()

    fun isLeaf(): Boolean = (children.count() == 0)

  }

  fun Component.isTextComponent(): Boolean {
    val textComponentsTypes = arrayOf(JLabel::class.java, JRadioButton::class.java, JCheckBox::class.java)
    return textComponentsTypes.any { it.isInstance(this) }
  }

  fun Component.getComponentText(): String? {
    when (this) {
      is JLabel -> return this.text
      is JRadioButton -> return this.text
      is JCheckBox -> return this.text
      else -> return null
    }
  }

  private fun findComponentByText(robot: Robot, container: Container, text: String, timeout: Timeout = Timeouts.seconds30): Component {
    return withPauseWhenNull(timeout = timeout) {
      robot.finder().findAll(container, ComponentMatcher { component ->
        component!!.isShowing && component.isTextComponent() && component.getComponentText() == text
      }).firstOrNull()
    }
  }

  fun <BoundedComponent> findBoundedComponentByText(robot: Robot,
                                                    container: Container,
                                                    text: String,
                                                    componentType: Class<BoundedComponent>,
                                                    timeout: Timeout = Timeouts.seconds30): BoundedComponent {
    return step("search component by nearby '$text' text") {
      val componentWithText = findComponentByText(robot, container, text, timeout)
      logInfo("found component '${componentWithText::class.java.canonicalName}': '$componentWithText'")
      if (componentWithText is JLabel && componentWithText.labelFor != null) {
        val labeledComponent = componentWithText.labelFor
        if (componentType.isInstance(labeledComponent)) return@step labeledComponent as BoundedComponent
        return@step robot.finder().find(labeledComponent as Container) { component -> componentType.isInstance(component) } as BoundedComponent
      }
      try {
        return@step withPauseWhenNull(timeout = timeout) {
          val componentsOfInstance = robot.finder().findAll(container,
                                                            ComponentMatcher { component -> componentType.isInstance(component) })
          componentsOfInstance.filter { it.isShowing && it.onHeightCenter(componentWithText, true) }
            .sortedBy { it.bounds.x }
            .firstOrNull()
        } as BoundedComponent
      }
      catch (e: WaitTimedOutError) {
        throw ComponentLookupException("Unable to find component of type: ${componentType.simpleName} in $container by text: $text")
      }
    }
  }

  //Does the textComponent intersects horizontal line going through the center of this component and lays lefter than this component
  fun Component.onHeightCenter(textComponent: Component, onLeft: Boolean): Boolean {
    val centerXAxis = this.bounds.height / 2 + this.locationOnScreen.y
    val sideCheck =
      if (onLeft)
        textComponent.locationOnScreen.x < this.locationOnScreen.x
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
   * waits for 30 sec timeout when functionProbeToNull() not return null
   *
   * @throws WaitTimedOutError with the text: "Timed out waiting for $timeout second(s) until {@code conditionText} will be not null"
   */
  fun <ReturnType> withPauseWhenNull(conditionText: String = "function to probe",
                                            timeout: Timeout = Timeouts.defaultTimeout,
                                            functionProbeToNull: () -> ReturnType?): ReturnType {
    var result: ReturnType? = null
    waitUntil("$conditionText will be not null", timeout) {
      result = functionProbeToNull()
      result != null
    }
    return result!!
  }

  fun waitUntil(condition: String, timeout: Timeout = Timeouts.defaultTimeout, conditionalFunction: () -> Boolean) {
    step("wait until $condition") {
      Pause.pause(object : Condition("${timeout.toPrintable()} until $condition") {
        override fun test() = conditionalFunction()
      }, timeout)
    }
  }

  fun <R> tryWithPause(exceptionClass: Class<out Exception>,
                   condition: String = "try block will not throw ${exceptionClass.name} exception",
                   timeout: Timeout,
                   tryBlock: () -> R): R {
    val exceptionRef: Ref<Exception> = Ref.create()
    try {
      return withPauseWhenNull (condition, timeout) {
        try {
          tryBlock()
        }
        catch (e: Exception) {
          if (exceptionClass.isInstance(e)) {
            exceptionRef.set(e)
            return@withPauseWhenNull null
          }
          throw e
        }
      }
    }
    catch (e: WaitTimedOutError) {
      throw Exception("Timeout for $condition exceeded ${timeout.toPrintable()}", exceptionRef.get())
    }
  }

  fun silentWaitUntil(condition: String, timeoutInSeconds: Int = 60, conditionalFunction: () -> Boolean) {
    try {
      Pause.pause(object : Condition("$timeoutInSeconds second(s) until $condition silently") {
        override fun test() = conditionalFunction()
      }, Timeout.timeout(timeoutInSeconds.toLong(), TimeUnit.SECONDS))
    }
    catch (ignore: WaitTimedOutError) {
    }
  }

  fun repeatUntil(condition: () -> Boolean, action: () -> Unit, maxAttempts: Int = 3) {
    var remainingAttempts = maxAttempts
    while (!condition() && remainingAttempts-- > 0) {
      try {
        action()
      } catch (e: Exception) {
        // ignore
      }
    }
    if (!condition()) {
      throw IllegalStateException("the condition is not satisfied")
    }
  }

  fun <T : Component> isComponentShowing(componentClass: Class<T>): Boolean = ignoreComponentLookupException {
    robot.findComponent(null, componentClass) { it.isShowing }
  } != null

  fun <ComponentType : Component> findAllWithBFS(container: Container, clazz: Class<ComponentType>): List<ComponentType> {
    val result = LinkedList<ComponentType>()
    val queue: Queue<Component> = LinkedList()

    @Suppress("UNCHECKED_CAST")
    fun check(container: Component) {
      if (clazz.isInstance(container)) result.add(container as ComponentType)
    }

    queue.add(container)
    while (queue.isNotEmpty()) {
      val polled = queue.poll()
      check(polled)
      if (polled is Container)
        queue.addAll(polled.components)
    }

    return result

  }

  fun <ComponentType : Component> waitUntilGone(robot: Robot,
                                                timeout: Timeout = Timeouts.seconds30,
                                                root: Container? = null,
                                                matcher: GenericTypeMatcher<ComponentType>) {
    return GuiTestUtil.waitUntilGone(root, timeout, matcher)
  }

  fun GuiTestCase.waitProgressDialogUntilGone(dialogTitle: String,
                                              predicate: FinderPredicate = Predicate.equality,
                                              timeoutToAppear: Timeout = Timeouts.seconds05,
                                              timeoutToGone: Timeout = Timeouts.defaultTimeout) {
    waitProgressDialogUntilGone(this.robot(), dialogTitle, predicate, timeoutToAppear, timeoutToGone)
  }

  fun waitProgressDialogUntilGone(robot: Robot,
                                  progressTitle: String,
                                  predicate: FinderPredicate = Predicate.equality,
                                  timeoutToAppear: Timeout = Timeouts.seconds30,
                                  timeoutToGone: Timeout = Timeouts.defaultTimeout) {
    step("search and wait for '$progressTitle' progress dialog disappearing") {
      //wait dialog appearance. In a bad case we could pass dialog appearance.
      var dialog: JDialog? = null
      try {
        waitUntil("progress dialog with title $progressTitle will appear", timeoutToAppear) {
          dialog = findProgressDialog(robot, progressTitle, predicate)
          dialog != null
        }
      }
      catch (ignoredTimeoutError: WaitTimedOutError) {}
      waitUntil("progress dialog with title $progressTitle will gone", timeoutToGone) { dialog == null || !dialog!!.isShowing }
    }
  }

  fun findProgressDialog(robot: Robot, progressTitle: String, predicate: FinderPredicate = Predicate.equality): JDialog? {
    return robot.finder().findAll(typeMatcher(JDialog::class.java) { dialog: JDialog ->
      findAllWithBFS(dialog, EngravedLabel::class.java).filter { it.isShowing && predicate(it.text, progressTitle) }.any()
    }).firstOrNull()
  }

  fun <ComponentType : Component?> typeMatcher(componentTypeClass: Class<ComponentType>,
                                               matcher: (ComponentType) -> Boolean): GenericTypeMatcher<ComponentType> {
    return object : GenericTypeMatcher<ComponentType>(componentTypeClass) {
      override fun isMatching(component: ComponentType): Boolean = matcher(component)
    }
  }


  fun <ReturnType> computeOnEdt(query: () -> ReturnType): ReturnType? = GuiActionRunner.execute(object : GuiQuery<ReturnType>() {
    override fun executeInEDT(): ReturnType = query()
  })

  fun <ReturnType> computeOnEdtWithTry(query: () -> ReturnType?): ReturnType? {
    val result = GuiActionRunner.execute(object : GuiQuery<Pair<ReturnType?, Throwable?>>() {
      override fun executeInEDT(): kotlin.Pair<ReturnType?, Throwable?> {
        return try {
          Pair(query(), null)
        }
        catch (e: Exception) {
          Pair(null, e)
        }
      }
    })
    if (result?.second != null) throw result.second!!
    return result?.first
  }

  inline fun <T> ignoreComponentLookupException(action: () -> T): T? = try {
    action()
  }
  catch (ignore: ComponentLookupException) {
    null
  }

  fun ensureCreateHasDone(guiTestCase: GuiTestCase) {
    try {
      com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntilGone(robot = guiTestCase.robot(),
                                                                     matcher = com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher(
                                                                       com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame::class.java) { it.isShowing })
    }
    catch (timeoutError: WaitTimedOutError) {
      with(guiTestCase) {
        welcomeFrame { button("Create").clickWhenEnabled() }
      }
    }
  }

  fun windowsShowing(): List<Window> {
    val listBuilder = ArrayList<Window>()
    Window.getWindows().filterTo(listBuilder) { it.isShowing }
    return listBuilder
  }

  fun fatalErrorsFromIde(afterDate: Date = Date(0)): List<Error> {
    val errorMessages = MessagePool.getInstance().getFatalErrors(true, true)
    val freshErrorMessages = errorMessages.filter { it.date > afterDate }
    val errors = mutableListOf<Error>()
    for (errorMessage in freshErrorMessages) {
      val messageBuilder = StringBuilder(errorMessage.message ?: "")
      val additionalInfo: String? = errorMessage.additionalInfo
      if (additionalInfo != null && additionalInfo.isNotEmpty())
        messageBuilder.append(System.getProperty("line.separator")).append("Additional Info: ").append(additionalInfo)
      val error = Error(messageBuilder.toString(), errorMessage.throwable)
      errors.add(error)
    }
    return Collections.unmodifiableList(errors)
  }

  fun waitForBackgroundTasks(robot: Robot, timeoutInSeconds: Int = 120) {
    Wait.seconds(timeoutInSeconds.toLong()).expecting("background tasks to finish")
      .until {
        robot.waitForIdle()

        val progressManager = ProgressManager.getInstance()
        !progressManager.hasModalProgressIndicator() &&
        !progressManager.hasProgressIndicator() &&
        !progressManager.hasUnsafeProgressIndicator()
      }
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
