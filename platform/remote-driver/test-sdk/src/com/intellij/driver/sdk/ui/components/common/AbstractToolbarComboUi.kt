package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.remoteDev.BeControlBuilder
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.BeControlComponentBase
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import org.jetbrains.annotations.Nls

fun Finder.abstractToolbarCombo(locator: QueryBuilder.() -> String) =
  x(AbstractToolbarComboUi::class.java) { locator() }

class AbstractToolbarComboUi(data: ComponentData) : UiComponent(data) {
  private val toolbarCombo by lazy { driver.cast(component, AbstractToolbarComboRef::class) }

  fun getText() = toolbarCombo.text
  fun getLeftIcons(): List<String> = toolbarCombo.leftIcons.map { it.toString() }
  fun getRightIcons(): List<String> = toolbarCombo.rightIcons.map { it.toString() }
}

class AbstractToolbarComboComponentClassBuilder : BeControlBuilder {
  override fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component {
    return AbstractToolbarComboBeControl(driver, frontendComponent, backendComponent)
  }
}

class AbstractToolbarComboBeControl(driver: Driver, frontendComponent: Component, backendComponent: Component) :
  BeControlComponentBase(driver, frontendComponent, backendComponent),
  AbstractToolbarComboRef {

  private val toolbarComboComponent: AbstractToolbarComboRef by lazy {
    driver.cast(onFrontend { byType("com.intellij.openapi.wm.impl.AbstractToolbarCombo") }.component, AbstractToolbarComboRef::class)
  }

  override var text: String?
    get() = toolbarComboComponent.text
    set(value) {
    }
  override var leftIcons: List<Icon>
    get() = toolbarComboComponent.leftIcons
    set(value) {
    }
  override var rightIcons: List<Icon>
    get() = toolbarComboComponent.rightIcons
    set(value) {
    }
}

@BeControlClass(AbstractToolbarComboComponentClassBuilder::class)
@Remote("com.intellij.openapi.wm.impl.AbstractToolbarCombo")
interface AbstractToolbarComboRef {
  var text: @Nls String?
  var leftIcons: List<Icon>
  var rightIcons: List<Icon>
}
