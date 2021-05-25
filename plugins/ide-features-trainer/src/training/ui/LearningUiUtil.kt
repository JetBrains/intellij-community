// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** It is a copy-paste from testGuiFramework to use minimal necessary subset to discover UI elements */
object LearningUiUtil {
  @Volatile
  private var myRobot: Robot? = null

  val robot: Robot
    get() {
      if(myRobot == null)
        synchronized(this) {
          if(myRobot == null) initializeRobot()
        }
      return myRobot ?: throw IllegalStateException("Cannot initialize the robot")
    }

  private fun initializeRobot() {
    if (myRobot != null) releaseRobot()
    myRobot = IftSmartWaitRobot()
  }

  private fun releaseRobot() {
    if(myRobot != null) {
      synchronized(this){
        if (myRobot != null){
          myRobot!!.cleanUpWithoutDisposingWindows()  // releases ScreenLock
          myRobot = null
        }
      }
    }
  }

  /**
   * Waits for a first component which passes the given matcher under the given root to become visible.
   */
  fun <T : Component> waitUntilFoundAll(robot: Robot,
                                        root: Container?,
                                        matcher: GenericTypeMatcher<T>,
                                        timeout: Timeout): Collection<T> {
    val reference = AtomicReference<Collection<T>>()
    Pause.pause(object : Condition("Find component using $matcher") {
      override fun test(): Boolean {
        val finder = robot.finder()
        val allFound = if (root != null) finder.findAll(root, matcher) else finder.findAll(matcher)
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
                                     root: Container?,
                                     matcher: GenericTypeMatcher<T>,
                                     timeout: Timeout): T {
    val allFound = waitUntilFoundAll(robot, root, matcher, timeout)
    if (allFound.size > 1) {
      // Only allow a single component to be found, otherwise you can get some really confusing
      // test failures; the matcher should pick a specific enough instance
      throw ComponentLookupException(
        "Found more than one " + matcher.supportedType().simpleName + " which matches the criteria: " + allFound)
    }
    return allFound.single()
  }

  fun <ComponentType : Component?> typeMatcher(componentTypeClass: Class<ComponentType>,
                                               matcher: (ComponentType) -> Boolean): GenericTypeMatcher<ComponentType> {
    return object : GenericTypeMatcher<ComponentType>(componentTypeClass) {
      override fun isMatching(component: ComponentType): Boolean = matcher(component)
    }
  }

  fun <ComponentType : Component> findShowingComponentWithTimeout(container: Container?,
                                                                  componentClass: Class<ComponentType>,
                                                                  timeout: Timeout = Timeout.timeout(10, TimeUnit.SECONDS),
                                                                  selector: ((candidates: Collection<ComponentType>) -> ComponentType?)? = null,
                                                                  finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
    try {
      return if (selector != null) {
        val result = waitUntilFoundAll(robot, container, typeMatcher(componentClass) { it.isShowing && finderFunction(it) }, timeout)
        selector(result) ?: throw ComponentLookupException("Cannot filter result component from: $result")
      }
      else {
        waitUntilFound(robot, container, typeMatcher(componentClass) { it.isShowing && finderFunction(it) }, timeout)
      }
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException(
        "Unable to find ${componentClass.simpleName} ${if (container != null) "in container $container" else ""} in ${timeout.duration()}(ms)")
    }
  }

  fun <ComponentType : Component> findAllShowingComponentWithTimeout(container: Container?,
                                                                     componentClass: Class<ComponentType>,
                                                                     timeout: Timeout = Timeout.timeout(10, TimeUnit.SECONDS),
                                                                     finderFunction: (ComponentType) -> Boolean = { true }): Collection<ComponentType> {
    try {
      return waitUntilFoundAll(robot, container, typeMatcher(componentClass) { it.isShowing && finderFunction(it) }, timeout)
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException(
        "Unable to find ${componentClass.simpleName} ${if (container != null) "in container $container" else ""} in ${timeout.duration()}(ms)")
    }
  }

  /**
   * function to find component of returning type inside a container (gets from receiver).
   *
   * @throws ComponentLookupException if desired component haven't been found under the container (gets from receiver) in specified timeout
   */
  inline fun <reified ComponentType : Component, ContainerComponentType : Container> ContainerFixture<ContainerComponentType>?.findComponentWithTimeout(
    timeout: Timeout = Timeout.timeout(10, TimeUnit.SECONDS),
    crossinline finderFunction: (ComponentType) -> Boolean = { true }): ComponentType {
    try {
      return waitUntilFound(robot, this?.target() as Container?,
                                        typeMatcher(ComponentType::class.java) { finderFunction(it) },
                                        timeout)
    }
    catch (e: WaitTimedOutError) {
      throw ComponentLookupException(
        "Unable to find ${ComponentType::class.java.name} ${if (this?.target() != null) "in container ${this.target()}" else ""} in ${timeout.duration()}")
    }
  }
}