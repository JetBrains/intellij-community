// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import org.assertj.swing.core.GenericTypeMatcher
import org.assertj.swing.core.Robot
import org.assertj.swing.exception.ComponentLookupException
import org.assertj.swing.exception.WaitTimedOutError
import org.assertj.swing.timing.Condition
import org.assertj.swing.timing.Pause
import org.assertj.swing.timing.Timeout
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.awt.Window
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** It is a copy-paste from testGuiFramework to use minimal necessary subset to discover UI elements */
object LearningUiUtil {
  @Volatile
  private var myRobot: Robot? = null

  val robot: Robot
    get() {
      if (myRobot == null)
        synchronized(this) {
          if (myRobot == null) initializeRobot()
        }
      return myRobot ?: throw IllegalStateException("Cannot initialize the robot")
    }

  val defaultComponentSearchShortTimeout = Timeout.timeout(500, TimeUnit.MILLISECONDS)

  private fun initializeRobot() {
    if (myRobot != null) releaseRobot()
    myRobot = IftSmartWaitRobot()
  }

  private fun releaseRobot() {
    if (myRobot != null) {
      synchronized(this) {
        if (myRobot != null) {
          myRobot!!.cleanUpWithoutDisposingWindows()  // releases ScreenLock
          myRobot = null
        }
      }
    }
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  private fun <T : Component> waitUntilFoundAll(robot: Robot,
                                                matcher: GenericTypeMatcher<T>,
                                                timeout: Timeout,
                                                getRoots: () -> Collection<Container>): Collection<T> {
    checkIsNotEdt()
    val reference = AtomicReference<Collection<T>>()
    Pause.pause(object : Condition("Find component using $matcher") {
      override fun test(): Boolean {
        val finder = robot.finder()
        val allFound = getRoots().flatMap { finder.findAll(it, matcher) }
        if (allFound.isNotEmpty()) {
          reference.set(allFound)
          return true
        }
        return false
      }
    }, timeout)

    return reference.get()
  }

  fun <T : Component> waitUntilFound(robot: Robot,
                                     matcher: GenericTypeMatcher<T>,
                                     timeout: Timeout,
                                     getRoots: () -> Collection<Container>): T {
    checkIsNotEdt()
    val allFound = waitUntilFoundAll(robot, matcher, timeout, getRoots)
    if (allFound.size > 1) {
      // Only allow a single component to be found, otherwise you can get some really confusing
      // test failures; the matcher should pick a specific enough instance
      val exceptionText = "Found more than one ${matcher.supportedType().simpleName} which matches the criteria: $allFound"
      thisLogger().warn(exceptionText)
      throw ComponentLookupException(exceptionText)
    }
    return allFound.single()
  }

  private fun checkIsNotEdt() {
    if (EDT.isCurrentThreadEdt()) {
      thisLogger().error("UI detection should not be called from the EDT thread. Please, move it to the background thread.")
    }
  }

  fun <ComponentType : Component?> typeMatcher(componentTypeClass: Class<ComponentType>,
                                               matcher: (ComponentType) -> Boolean): GenericTypeMatcher<ComponentType> {
    return object : GenericTypeMatcher<ComponentType>(componentTypeClass) {
      override fun isMatching(component: ComponentType): Boolean = matcher(component)
    }
  }

  fun getUiRootsForProject(project: Project): List<Container> {
    return robot.hierarchy().roots().filter { it is IdeFrame && it.isShowing && it.project == project }
  }

  private fun isReallyVisible(component: Component): Boolean {
    val window = UIUtil.getParentOfType(Window::class.java, component) ?: return true
    val locationOnScreen = component.locationOnScreen
    val onScreenRect = Rectangle(locationOnScreen.x, locationOnScreen.y, component.width, component.height)
    val bounds = window.bounds
    return bounds.intersects(onScreenRect)
  }

  fun <ComponentType : Component> findShowingComponentWithTimeout(project: Project,
                                                                  componentClass: Class<ComponentType>,
                                                                  timeout: Timeout = Timeout.timeout(10, TimeUnit.SECONDS),
                                                                  selector: ((candidates: Collection<ComponentType>) -> ComponentType?)? = null,
                                                                  finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
    try {
      val matcher = typeMatcher(componentClass) {
        it.isShowing && finderFunction(it) && isReallyVisible(it)
      }
      return if (selector != null) {
        val result = waitUntilFoundAll(robot, matcher, timeout) { getUiRootsForProject(project) }
        selector(result) ?: throw ComponentLookupException("Cannot filter result component from: $result")
      }
      else {
        waitUntilFound(robot, matcher, timeout) { getUiRootsForProject(project) }
      }
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException(
        "Unable to find ${componentClass.simpleName} in containers ${getUiRootsForProject(project)} in ${timeout.duration()}(ms)")
    }
  }

  fun <ComponentType : Component> findAllShowingComponentWithTimeout(project: Project,
                                                                     componentClass: Class<ComponentType>,
                                                                     timeout: Timeout = Timeout.timeout(10, TimeUnit.SECONDS),
                                                                     finderFunction: (ComponentType) -> Boolean = { true }): Collection<ComponentType> {
    try {
      val matcher = typeMatcher(componentClass) {
        it.isShowing && finderFunction(it) && isReallyVisible(it)
      }
      return waitUntilFoundAll(robot, matcher, timeout) { getUiRootsForProject(project) }
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException(
        "Unable to find ${componentClass.simpleName} in containers ${getUiRootsForProject(project)} in ${timeout.duration()}(ms)")
    }
  }

  /**
   * function to find component of returning type inside a container (gets from receiver).
   *
   * @throws ComponentLookupException if desired component haven't been found under the container (gets from receiver) in specified timeout
   */
  inline fun <reified ComponentType : Component, ContainerComponentType : Container> IftTestContainerFixture<ContainerComponentType>.findComponentWithTimeout(
    timeout: Timeout = Timeout.timeout(10, TimeUnit.SECONDS),
    crossinline finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
    try {
      return waitUntilFound(robot,
        typeMatcher(ComponentType::class.java) { finderFunction(it) },
        timeout) { listOf(this.target() as Container) }
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException(
        "Unable to find ${ComponentType::class.java.name} in container ${this.target()} in ${timeout.duration()}")
    }
  }

  fun <ComponentType : Component> findComponentOrNull(project: Project,
                                                      componentClass: Class<ComponentType>,
                                                      selector: ((candidates: Collection<ComponentType>) -> ComponentType?)? = null,
                                                      finderFunction: (ComponentType) -> Boolean = { true }): ComponentType? {
    return try {
      findShowingComponentWithTimeout(project, componentClass, defaultComponentSearchShortTimeout, selector, finderFunction)
    }
    catch (e: WaitTimedOutError) {
      null
    }
    catch (e: ComponentLookupException) {
      null
    }
  }
}