// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class ResetFontSizeAppInitListener : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment || app.isUnitTestMode) {
      return
    }

    asyncScope.launch {
      resetFontSizeAppInitListener(force = true,
                                   propertyManager = app.serviceAsync<PropertiesComponent>(),
                                   editorColorManager = app.serviceAsync<EditorColorsManager>())
    }
  }
}

private class ResetFontSizeEditorActionHandler : EditorColorsListener {
  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    resetFontSizeAppInitListener(force = false,
                                 propertyManager = PropertiesComponent.getInstance(),
                                 editorColorManager = EditorColorsManager.getInstance())
  }
}

private fun resetFontSizeAppInitListener(force: Boolean, propertyManager: PropertiesComponent, editorColorManager: EditorColorsManager) {
  val globalScheme = editorColorManager.getGlobalScheme()
  if (force || propertyManager.getValue(ResetFontSizeAction.PREVIOUS_COLOR_SCHEME, "") != globalScheme.getName()) {
    propertyManager.setValue(ResetFontSizeAction.PREVIOUS_COLOR_SCHEME, globalScheme.getName())
    propertyManager.setValue(ResetFontSizeAction.UNSCALED_FONT_SIZE_TO_RESET_CONSOLE, globalScheme.consoleFontSize2D, -1f)
    propertyManager.setValue(ResetFontSizeAction.UNSCALED_FONT_SIZE_TO_RESET_EDITOR, globalScheme.editorFontSize2D, -1f)
  }
}