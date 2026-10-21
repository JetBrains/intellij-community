// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.typePresentation

import com.intellij.ide.TypePresentationService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.platform.testFramework.plugins.PluginTestHandle

/**
 * Asks for the type presentation of its own class on dispose, so the lookup runs during the unload of the plugin.
 */
@Service
internal class TypePresentationLookupService : PluginTestHandle<Unit, Unit>, Disposable {
  override fun test(arg: Unit) {
  }

  override fun dispose() {
    TypePresentationService.getService().getTypePresentableName(TypePresentationLookupService::class.java)
  }
}
