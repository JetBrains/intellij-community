package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.linkLabel(labelText: String): JLabelUiComponent =
  x(JLabelUiComponent::class.java) { and(byType("com.intellij.ui.components.labels.LinkLabel"), byAccessibleName(labelText)) }

class JLabelUiComponent(data: ComponentData) : UiComponent(data) {

  private val fixture by lazy { driver.cast(component, JLabelRef::class) }

  fun getText(): String = fixture.getText().orEmpty()
}

@Remote("javax.swing.JLabel")
interface JLabelRef {

  fun getText(): String?
}