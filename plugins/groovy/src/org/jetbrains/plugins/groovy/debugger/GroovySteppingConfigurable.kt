// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.debugger

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.plugins.groovy.GroovyBundle

internal class GroovySteppingConfigurable(private val settings: GroovyDebuggerSettings) :
  BoundSearchableConfigurable(GroovyBundle.message("groovy.debug.caption"), "reference.idesettings.debugger.groovy") {

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(GroovyBundle.message("groovy.debug.disable.specific.methods"))
          .bindSelected({ settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS ?: true }, { settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = it })
      }
    }
  }
}
