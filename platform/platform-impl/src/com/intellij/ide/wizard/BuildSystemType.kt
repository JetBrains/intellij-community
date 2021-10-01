// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel

interface BuildSystemType<T, P> {
  val settingsFactory: () -> P
  val name: String

  fun advancedSettings(settings: P): DialogPanel
  fun setupProject(project: Project, languageSettings: T, settings: P)
}

open class BuildSystemWithSettings<T, P>(buildSystemType: BuildSystemType<T, P>) : BuildSystemType<T, P> by buildSystemType {
  val settings = lazy { settingsFactory.invoke() }
  val advancedSettings = lazy { advancedSettings(settings.value) }

  fun advancedSettings() : DialogPanel = advancedSettings.value

  fun setupProject(project: Project, languageSettings: T) {
    advancedSettings().apply()
    setupProject(project, languageSettings, settings.value)
  }
}