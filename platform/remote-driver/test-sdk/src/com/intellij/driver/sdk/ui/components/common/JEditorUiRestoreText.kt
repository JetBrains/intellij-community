package com.intellij.driver.sdk.ui.components.common

/** Sets the editor text back to [text] when a test changed it. A failure is printed, not thrown. */
fun JEditorUiComponent.restoreText(text: String) {
  runCatching { if (this.text != text) this.text = text }
    .onFailure { println("Could not restore the editor text: ${it.message}") }
}
