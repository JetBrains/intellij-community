// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.ide.ApplicationActivity
import com.intellij.idea.AppMode
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.util.application

/**
 * Finds the fonts for the soft wrap signs before the first soft-wrapped editor needs them.
 * Otherwise, that editor does the first search on the EDT when it gets its width.
 */
internal class SoftWrapSignFontPreloader : ApplicationActivity {
  init {
    if (application.isHeadlessEnvironment || AppMode.isRemoteDevHost()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    // A per-client service, so `serviceAsync` does not find it in the application container.
    if (!EditorSettingsExternalizable.getInstance().isUseSoftWraps) {
      return
    }
    CompositeSoftWrapPainter.preloadSignFonts(serviceAsync<EditorColorsManager>().globalScheme.fontPreferences)
  }
}
