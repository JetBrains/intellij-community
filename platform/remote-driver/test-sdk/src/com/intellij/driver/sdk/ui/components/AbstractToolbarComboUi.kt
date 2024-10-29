package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.remoteDev.BeControlAdapter
import com.intellij.driver.sdk.remoteDev.BeControlComponentBase
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.remote.Robot
import org.jetbrains.annotations.Nls

fun Finder.abstractToolbarCombo(locator: QueryBuilder.() -> String) =
  x(AbstractToolbarComboUi::class.java) { locator() }

class AbstractToolbarComboUi(data: ComponentData) : UiComponent(data) {
  val fixture = driver.new(AbstractToolbarComboRef::class, robot, component)

  fun getText() = fixture.text
  fun getLeftIcons(): List<String> = fixture.leftIcons.map { it.toString() }
  fun getRightIcons(): List<String> = fixture.rightIcons.map { it.toString() }
}


class AbstractToolbarComboAdapter(robot: Robot, component: BeControlComponentBase) :
  BeControlComponentBase(component.driver, component.frontendComponent, component.backendComponent),
  AbstractToolbarComboRef {

  private val fixture: AbstractToolbarComboRef by lazy {
    driver.cast(onFrontend { contains(byAttribute("classhierarchy", "AbstractToolbarCombo")) }.component, AbstractToolbarComboRef::class)
  }

  override var text: String?
    get() = fixture.text
    set(value) {
    }
  override var leftIcons: List<Icon>
    get() = fixture.leftIcons
    set(value) {
    }
  override var rightIcons: List<Icon>
    get() = fixture.rightIcons
    set(value) {
    }
}

@BeControlAdapter(AbstractToolbarComboAdapter::class)
@Remote("com.intellij.openapi.wm.impl.AbstractToolbarCombo")
interface AbstractToolbarComboRef {
  var text: @Nls String?
  var leftIcons: List<Icon>
  var rightIcons: List<Icon>
}
