package com.intellij.driver.sdk.python

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.components.UiComponent

/**
 * Clicks the button on the EDT, and sends no native mouse gesture.
 *
 * Use it when the IDE event queue takes the robot gesture before the button gets it. [click] then
 * logs "Click was unsuccessful", and the action of the button does not run. See PCQA-1803 and
 * AT-5091.
 *
 * The component must be a `javax.swing.JButton`.
 */
fun UiComponent.clickOnEdt() {
  val target = component
  driver.withContext(OnDispatcher.EDT) {
    cast(target, JButtonClickRef::class).doClick()
  }
}

/** The click entry point of a Swing button. */
@Remote("javax.swing.JButton")
interface JButtonClickRef {
  fun doClick()
}
