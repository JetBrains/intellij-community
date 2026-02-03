package com.intellij.driver.sdk.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.getEditorColorsManagerInstance() = utility(EditorColorsManagerImpl::class).getInstance()

@Remote("com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl")
interface EditorColorsManagerImpl {
  fun getInstance(): EditorColorsManagerImpl
  val schemeManager: SchemeManager
}

@Remote("com.intellij.openapi.editor.colors.EditorColorsScheme")
interface EditorColorsScheme

@Remote("com.intellij.openapi.options.SchemeManager")
interface SchemeManager {
  val currentSchemeName: String?
  fun setCurrent(scheme: EditorColorsScheme?, notify: Boolean = true, processChangeSynchronously: Boolean = false)
  fun findSchemeByName(schemeName: String): EditorColorsScheme?
}



