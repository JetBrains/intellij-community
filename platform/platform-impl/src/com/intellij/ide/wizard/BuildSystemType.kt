// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.LabelAndComponent

abstract class BuildSystemType<T>(open val name: String) {
  open val settings: List<LabelAndComponent>
    get() = emptyList()
  open val advancedSettings: List<LabelAndComponent>
    get() = emptyList()

  abstract fun setupProject(settings: T)
}