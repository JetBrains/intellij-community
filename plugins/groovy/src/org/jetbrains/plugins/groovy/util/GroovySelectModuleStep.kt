// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.util.PlatformIcons
import org.jetbrains.plugins.groovy.GroovyBundle.message

class GroovySelectModuleStep(
  modules: List<Module>,
  private val consumer: (Module) -> Unit
) : BaseListPopupStep<Module>(message("select.module.description"), modules, PlatformIcons.CONTENT_ROOT_ICON_CLOSED) {

  override fun getTextFor(value: Module): String = value.name

  override fun getIndexedString(value: Module): String = value.name

  override fun isSpeedSearchEnabled(): Boolean = true

  override fun onChosen(selectedValue: Module, finalChoice: Boolean): PopupStep<*>? {
    consumer(selectedValue)
    return null
  }
}
