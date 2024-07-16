package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote

class JLabelUiComponent(data: ComponentData) : UiComponent(data) {

  private val fixture by lazy { driver.cast(component, JLabelRef::class) }

  fun getText() = fixture.getText()
}

@Remote("javax.swing.JLabel")
interface JLabelRef {

  fun getText(): String
}