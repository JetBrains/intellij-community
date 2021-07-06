package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.extensions.ExtensionPointName

interface EditorColorsManagerListener {
  fun schemesReloaded()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorColorsManagerListener> = ExtensionPointName.create("com.intellij.editorColorsManagerListener")
  }
}