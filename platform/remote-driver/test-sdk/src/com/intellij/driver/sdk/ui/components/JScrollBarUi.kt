package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JScrollBar

fun Finder.verticalScrollBar(f: JScrollBarUi.() -> Unit = {}) =
  xx(xQuery { byType (JScrollBar::class.java) }, JScrollBarUi::class.java).list().single { it.getOrientation() == JScrollBar.VERTICAL }.apply(f)

class JScrollBarUi(data: ComponentData): UiComponent(data) {
  private val scrollBar get() = driver.cast(component, JScrollBarComponent::class)
  private val fixture by lazy { driver.new(JScrollBarFixture::class, robot, component) }

  fun getOrientation() = scrollBar.getOrientation()

  fun scrollToMaximum() {
    fixture.scrollToMaximum()
  }

  fun scrollToMinimum() {
    fixture.scrollToMinimum()
  }
}

@Remote("javax.swing.JScrollBar")
interface JScrollBarComponent {
  fun getOrientation(): Int
}

@Remote("org.assertj.swing.fixture.JScrollBarFixture")
interface JScrollBarFixture {
  fun scrollToMaximum(): JScrollBarFixture
  fun scrollToMinimum(): JScrollBarFixture
}