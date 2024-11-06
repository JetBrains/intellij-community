package com.intellij.driver.sdk.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.RdTarget
import com.intellij.driver.model.transport.Ref
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.SearchContext
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.*
import org.w3c.dom.Element
import java.awt.Point
import java.awt.Rectangle
import kotlin.reflect.KClass


class BeControlComponentBuilder : BeControlBuilder {
  override fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component {
    return BeControlComponentBase(driver, frontendComponent, backendComponent)
  }
}

open class BeControlComponentBase(
  val driver: Driver,
  val frontendComponent: Component,
  val backendComponent: Component
) : Component, RefWrapper {

  private val frontendUi: UiRobot by lazy {
    getUiRobot(frontendComponent, RdTarget.FRONTEND)
  }
  private val backendUi: UiRobot by lazy {
    getUiRobot(backendComponent, RdTarget.BACKEND)
  }

  private fun getUiRobot(component: Component, rdTarget: RdTarget): UiRobot {
    val searchService = SearchService(driver.service(SwingHierarchyService::class, rdTarget), driver)
    val searchContext = object : SearchContext {
      override val context: String = ""

      override fun findAll(xpath: String): List<Component> {
        return searchService.findAll(xpath, component, true)
      }
    }
    return UiRobot(driver, searchService, searchContext, RobotProvider(driver))
  }

  private fun getFrontendRef(): Ref = (frontendComponent as RefWrapper).getRef()
  private fun getBackendRef(): Ref = (backendComponent as RefWrapper).getRef()

  protected fun <T : UiComponent> onFrontend(type: KClass<T>, locator: QueryBuilder.() -> String): T {
    return frontendUi.x(type.java, locator)
  }

  protected fun onFrontend(locator: QueryBuilder.() -> String): UiComponent {
    return frontendUi.x(locator)
  }

  override val x: Int
    get() = frontendComponent.x
  override val y: Int
    get() = frontendComponent.y
  override val width: Int
    get() = frontendComponent.width
  override val height: Int
    get() = frontendComponent.height

  override fun getBounds(): Rectangle {
    return frontendComponent.getBounds()
  }

  override fun isVisible(): Boolean {
    return frontendComponent.isVisible()
  }

  override fun isShowing(): Boolean {
    return frontendComponent.isShowing()
  }

  override fun isEnabled(): Boolean {
    return frontendComponent.isEnabled()
  }

  override fun isFocusOwner(): Boolean {
    return frontendComponent.isFocusOwner()
  }

  override fun getLocationOnScreen(): Point {
    return frontendComponent.getLocationOnScreen()
  }

  override fun getClass(): Class {
    return backendComponent.getClass()
  }

  override fun getForeground(): ColorRef {
    return frontendComponent.getForeground()
  }

  override fun getBackground(): ColorRef {
    return frontendComponent.getBackground()
  }

  override fun getAccessibleContext(): AccessibleContextRef? {
    return frontendComponent.getAccessibleContext()
  }

  override fun getParent(): Component {
    return frontendComponent.getParent()
  }

  override fun isDisplayable(): Boolean {
    return frontendComponent.isDisplayable()
  }

  override fun getRef() = getFrontendRef()

  override fun getRefPluginId() = ""
}

fun getFrontendRef(element: Element) = Ref(
  element.getAttribute("frontend_refId"),
  element.getAttribute("frontend_javaclass"),
  element.getAttribute("frontend_hashCode").toInt(),
  element.getAttribute("frontend_asString"),
  RdTarget.FRONTEND
)

fun getBackendRef(element: Element) = Ref(
  element.getAttribute("backend_refId"),
  element.getAttribute("backend_javaclass"),
  element.getAttribute("backend_hashCode").toInt(),
  element.getAttribute("backend_asString"),
  RdTarget.BACKEND
)

fun validateBeControlElement(element: Element): Boolean {
  val attrNames = listOf("refId", "javaclass", "hashCode", "asString")
  val necessaryAttributes = attrNames.map { "frontend_$it" } + attrNames.map { "backend_$it" }
  return necessaryAttributes.all { element.hasAttribute(it) }
}