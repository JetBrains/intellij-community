// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.model.ui.UiActionResult
import com.intellij.driver.model.ui.UiClickRequest
import com.intellij.driver.model.ui.UiDragRequest
import com.intellij.driver.model.ui.UiFillRequest
import com.intellij.driver.model.ui.UiLocator
import com.intellij.driver.model.ui.UiNode
import com.intellij.driver.model.ui.UiRole
import com.intellij.driver.model.ui.UiSnapshot
import com.intellij.driver.sdk.ui.remote.UiAutomationService
import kotlin.time.Duration

class Locator internal constructor(
  private val driver: Driver,
  internal val selector: UiLocator,
) {
  private val service: UiAutomationService
    get() = driver.service(UiAutomationService::class)

  fun element(timeout: Duration = DEFAULT_FIND_TIMEOUT): UiNode? {
    return service.find(selector, timeout.inWholeMilliseconds)
  }

  fun click(
    button: RemoteMouseButton = RemoteMouseButton.LEFT,
    clickCount: Int = 1,
    timeout: Duration = DEFAULT_FIND_TIMEOUT,
  ): UiActionResult {
    return service.click(UiClickRequest(selector, button, clickCount, timeout.inWholeMilliseconds))
  }

  fun fill(text: String, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiActionResult {
    return service.fill(UiFillRequest(selector, text, timeout.inWholeMilliseconds))
  }

  fun dragTo(target: Locator, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiActionResult {
    require(driver === target.driver) { "Both locators must belong to the same Driver" }
    return service.drag(UiDragRequest(selector, target.selector, timeout.inWholeMilliseconds))
  }

  fun locator(descendant: Locator): Locator {
    require(driver === descendant.driver) { "Both locators must belong to the same Driver" }
    return Locator(driver, UiLocator.Within(selector, descendant.selector))
  }

  fun getByAccessibleName(name: String, exact: Boolean = true): Locator {
    return descendant(UiLocator.AccessibleName(name, exact))
  }

  fun getByRole(role: UiRole, name: String? = null, exactName: Boolean = true): Locator {
    return descendant(UiLocator.Role(role, name, exactName))
  }

  fun getByText(text: String, exact: Boolean = false): Locator {
    return descendant(UiLocator.Text(text, exact))
  }

  fun getByClassName(className: String): Locator {
    return descendant(UiLocator.ClassName(className))
  }

  fun locator(xpath: String): Locator = descendant(UiLocator.Xpath(xpath))

  fun first(): Locator = nth(0)

  fun nth(index: Int): Locator = Locator(driver, UiLocator.Nth(selector, index))

  private fun descendant(locator: UiLocator): Locator = Locator(driver, UiLocator.Within(selector, locator))
}

fun UiRobot.getByAccessibleName(name: String, exact: Boolean = true): Locator {
  return Locator(driver, UiLocator.AccessibleName(name, exact))
}

fun UiRobot.getByRole(role: UiRole, name: String? = null, exactName: Boolean = true): Locator {
  return Locator(driver, UiLocator.Role(role, name, exactName))
}

fun UiRobot.getByText(text: String, exact: Boolean = false): Locator {
  return Locator(driver, UiLocator.Text(text, exact))
}

fun UiRobot.getByClassName(className: String): Locator {
  return Locator(driver, UiLocator.ClassName(className))
}

fun UiRobot.locator(xpath: String): Locator {
  return Locator(driver, UiLocator.Xpath(xpath))
}

fun UiRobot.snapshot(): UiSnapshot = driver.service(UiAutomationService::class).snapshot()
