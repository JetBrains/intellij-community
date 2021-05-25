// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.KeyStrokeAdapter
import com.intellij.ui.MultilineTreeCellRenderer
import com.intellij.ui.SimpleColoredComponent
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.driver.BasicJListCellReader
import org.fest.swing.driver.ComponentDriver
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.AbstractComponentFixture
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import training.ui.LearningUiUtil
import training.ui.LearningUiUtil.findComponentWithTimeout
import training.util.invokeActionForFocusContext
import java.awt.Component
import java.awt.Container
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import kotlin.collections.ArrayList

@LearningDsl
class TaskTestContext(rt: TaskRuntimeContext): TaskRuntimeContext(rt) {
  private val defaultTimeout = Timeout.timeout(3, TimeUnit.SECONDS)

  val robot: Robot get() = LearningUiUtil.robot

  data class TestScriptProperties (
    val duration: Int = 15, //seconds
    val skipTesting: Boolean = false
  )

  fun type(text: String) {
    robot.waitForIdle()
    for (element in text) {
      robot.type(element)
      Pause.pause(10, TimeUnit.MILLISECONDS)
    }
    Pause.pause(300, TimeUnit.MILLISECONDS)
  }

  fun actions(vararg actionIds: String) {
    for (actionId in actionIds) {
      val action = ActionManager.getInstance().getAction(actionId) ?: error("Action $actionId is non found")
      invokeActionForFocusContext(action)
    }
  }

  fun ideFrame(action: ContainerFixture<IdeFrameImpl>.() -> Unit) {
    with(findIdeFrame(robot, defaultTimeout)) {
      action()
    }
  }

  fun <ComponentType : Component> waitComponent(componentClass: Class<ComponentType>, partOfName: String? = null) {
    LearningUiUtil.waitUntilFound(robot, null, typeMatcher(componentClass) {
      (if (partOfName != null) it.javaClass.name.contains(partOfName)
      else true) && it.isShowing
    }, defaultTimeout)
  }

  /**
   * Finds a JList component in hierarchy of context component with a containingItem and returns JListFixture.
   *
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <C : Container> ContainerFixture<C>.jList(containingItem: String? = null, timeout: Timeout = defaultTimeout): JListFixture {
    return generalListFinder(timeout, containingItem) { element, p ->  element == p }
  }

  /**
   * Finds a JButton component in hierarchy of context component with a name and returns ExtendedButtonFixture.
   *
   * @throws ComponentLookupException if component has not been found or timeout exceeded
   */
  fun <C : Container> ContainerFixture<C>.button(name: String, timeout: Timeout = defaultTimeout): JButtonFixture {
    val jButton: JButton = findComponentWithTimeout(timeout) { it.isShowing && it.isVisible && it.text == name }
    return JButtonFixture(robot(), jButton)
  }

  fun <C : Container> ContainerFixture<C>.actionButton(actionName: String, timeout: Timeout = defaultTimeout)
    : AbstractComponentFixture<*, ActionButton, ComponentDriver<*>> {
    val actionButton: ActionButton = findComponentWithTimeout(timeout) {
      it.isShowing && it.isEnabled && actionName == it.action.templatePresentation.text
    }
    return ActionButtonFixture(robot(), actionButton)
  }

  // Modified copy-paste
  fun <C : Container> ContainerFixture<C>.jListContains(partOfItem: String? = null, timeout: Timeout = defaultTimeout): JListFixture {
    return generalListFinder(timeout, partOfItem) { element, p -> element.contains(p) }
  }

  /**
   * Finds JDialog with a specific title (if title is null showing dialog should be only one) and returns created JDialogFixture
   */
  fun dialog(title: String? = null,
             ignoreCaseTitle: Boolean = false,
             predicate: (String, String) -> Boolean = { found: String, wanted: String -> found == wanted },
             timeout: Timeout = defaultTimeout,
             func: ContainerFixture<JDialog>.() -> Unit = {})
    : AbstractComponentFixture<*, JDialog, ComponentDriver<*>> {
    val jDialogFixture = if (title == null) {
      val jDialog = LearningUiUtil.waitUntilFound(robot, null, typeMatcher(JDialog::class.java) { true }, timeout)
      JDialogFixture(robot, jDialog)
    }
    else {
      try {
        val dialog = withPauseWhenNull(timeout = timeout) {
          val allMatchedDialogs = robot.finder().findAll(typeMatcher(JDialog::class.java) {
            it.isFocused &&
            if (ignoreCaseTitle) predicate(it.title.toLowerCase(), title.toLowerCase()) else predicate(it.title, title)
          }).filter { it.isShowing && it.isEnabled && it.isVisible }
          if (allMatchedDialogs.size > 1) throw Exception(
            "Found more than one (${allMatchedDialogs.size}) dialogs matched title \"$title\"")
          allMatchedDialogs.firstOrNull()
        }
        JDialogFixture(robot, dialog)
      }
      catch (timeoutError: WaitTimedOutError) {
        throw ComponentLookupException("Timeout error for finding JDialog by title \"$title\" for ${timeout.duration()}")
      }
    }
    func(jDialogFixture)
    return jDialogFixture
  }

  fun ContainerFixture<*>.jComponent(target: Component): AbstractComponentFixture<*, Component, ComponentDriver<*>> {
    return SimpleComponentFixture(robot(), target)
  }

  fun invokeActionViaShortcut(shortcut: String) {
    val keyStroke = KeyStrokeAdapter.getKeyStroke(shortcut)
    robot.pressAndReleaseKey(keyStroke.keyCode, keyStroke.modifiers)
  }

  companion object {
    @Volatile
    var inTestMode: Boolean = false
  }

  ////////////------------------------------
  private fun <C : Container> ContainerFixture<C>.generalListFinder(timeout: Timeout,
                                                                    containingItem: String?,
                                                                    predicate: (String, String) -> Boolean): JListFixture {
    val extCellReader = ExtendedJListCellReader()
    val myJList: JList<*> = findComponentWithTimeout(timeout) { jList: JList<*> ->
      if (containingItem == null) true //if were searching for any jList()
      else {
        val elements = (0 until jList.model.size).mapNotNull { extCellReader.valueAt(jList, it) }
        elements.any { predicate(it, containingItem) } && jList.isShowing
      }
    }
    val jListFixture = JListFixture(robot(), myJList)
    jListFixture.replaceCellReader(extCellReader)
    return jListFixture
  }

  /**
   * waits for  [timeout] when functionProbeToNull() not return null
   *
   * @throws WaitTimedOutError with the text: "Timed out waiting for $timeout second(s) until {@code conditionText} will be not null"
   */
  private fun <ReturnType> withPauseWhenNull(conditionText: String = "function to probe",
                                             timeout: Timeout = defaultTimeout,
                                             functionProbeToNull: () -> ReturnType?): ReturnType {
    var result: ReturnType? = null
    waitUntil("$conditionText will be not null", timeout) {
      result = functionProbeToNull()
      result != null
    }
    return result!!
  }

  private fun waitUntil(condition: String, timeout: Timeout = defaultTimeout, conditionalFunction: () -> Boolean) {
    Pause.pause(object : Condition("${timeout.duration()} until $condition") {
      override fun test() = conditionalFunction()
    }, timeout)
  }


  private class SimpleComponentFixture(robot: Robot, target: Component) :
    ComponentFixture<SimpleComponentFixture, Component>(SimpleComponentFixture::class.java, robot, target)


  private fun <ComponentType : Component?> typeMatcher(componentTypeClass: Class<ComponentType>,
                                                       matcher: (ComponentType) -> Boolean): GenericTypeMatcher<ComponentType> {
    return object : GenericTypeMatcher<ComponentType>(componentTypeClass) {
      override fun isMatching(component: ComponentType): Boolean = matcher(component)
    }
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


private class ExtendedJListCellReader : BasicJListCellReader() {
  override fun valueAt(list: JList<*>, index: Int): String? {
    val element = list.model.getElementAt(index) ?: return null
    @Suppress("UNCHECKED_CAST")
    val cellRendererComponent = (list as JList<Any>).cellRenderer
      .getListCellRendererComponent(list, element, index, true, true)
    return getValueWithCellRenderer(cellRendererComponent)
  }
}

private fun SimpleColoredComponent.getFullText(): String {
  return invokeAndWaitIfNeeded {
    this.getCharSequence(false).toString()
  }
}


private fun SimpleColoredComponent.getFirstText(): String {
  return invokeAndWaitIfNeeded {
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
          it.getFullText().isNotEmpty()
        }
        .map {
          it.getFullText()
        }
        .toList()
    )
    return resultList.firstOrNull { it.isNotEmpty() }
  }
  catch (ignored: ComponentLookupException) {
    return null
  }
}

private fun <ComponentType : Component> findAllWithBFS(container: Container, clazz: Class<ComponentType>): List<ComponentType> {
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

private open class ComponentFixture<S, C : Component>(selfType: Class<S>, robot: Robot, target: C)
  : AbstractComponentFixture<S, C, ComponentDriver<*>>(selfType, robot, target) {

  override fun createDriver(robot: Robot): ComponentDriver<*> {
    return ComponentDriver<Component>(robot)
  }
}

private class ActionButtonFixture(robot: Robot, target: ActionButton):
  ComponentFixture<ActionButtonFixture, ActionButton>(ActionButtonFixture::class.java, robot, target), ContainerFixture<ActionButton>

private class IdeFrameFixture(robot: Robot, target: IdeFrameImpl)
  : ComponentFixture<IdeFrameFixture, IdeFrameImpl>(IdeFrameFixture::class.java, robot, target), ContainerFixture<IdeFrameImpl>

private class JDialogFixture(robot: Robot, jDialog: JDialog) :
  ComponentFixture<JDialogFixture, JDialog>(JDialogFixture::class.java, robot, jDialog), ContainerFixture<JDialog>

private fun findIdeFrame(robot: Robot, timeout: Timeout): IdeFrameFixture {
  val matcher: GenericTypeMatcher<IdeFrameImpl> = object : GenericTypeMatcher<IdeFrameImpl>(
    IdeFrameImpl::class.java) {
    override fun isMatching(frame: IdeFrameImpl): Boolean {
      return true
    }
  }
  return try {
    Pause.pause(object : Condition("IdeFrame to show up") {
      override fun test(): Boolean {
        return !robot.finder().findAll(matcher).isEmpty()
      }
    }, timeout)
    val ideFrame = robot.finder().find(matcher)
    IdeFrameFixture(robot, ideFrame)
  }
  catch (timedOutError: WaitTimedOutError) {
    throw ComponentLookupException("Unable to find IdeFrame in " + timeout.duration())
  }
}
