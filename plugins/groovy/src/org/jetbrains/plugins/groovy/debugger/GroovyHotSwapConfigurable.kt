// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.plugins.groovy.GroovyBundle

class GroovyHotSwapConfigurable(
  private val settings: GroovyDebuggerSettings
) : BoundSearchableConfigurable(
  _id = "reference.idesettings.debugger.groovy",
  displayName = GroovyBundle.message("groovy.debug.caption"),
  helpTopic = "reference.idesettings.debugger.groovy"
) {

  override fun createPanel(): DialogPanel = panel {
    row {
      checkBox(GroovyBundle.message("configurable.hotswap.checkbox"))
        .comment(GroovyBundle.message("configurable.hotswap.checkbox.description"))
        .bindSelected(settings::ENABLE_GROOVY_HOTSWAP)
    }
  }
}
