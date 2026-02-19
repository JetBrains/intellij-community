// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.settings

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableWithId

internal object ConfigurableCollectorAction {
  fun get(id: String): ConfigurableWithId {
    return getAllConfigurables().first { it.id == id }
  }

  fun getAllConfigurableIds(): List<String> {
    return getAllConfigurables().map { it.id }
  }

  private fun getAllConfigurables(): List<ConfigurableWithId> {
    val list = mutableListOf<ConfigurableWithId>()
    ShowSettingsUtilImpl.getConfigurableGroups(null, true)[0]
      .configurables.forEach {
        collectConfigurables(list, it)
      }
    return list
  }

  private fun collectConfigurables(list: MutableList<ConfigurableWithId>, configurable: Configurable) {
    if (configurable is ConfigurableWithId) {
      list.add(configurable)
    } else {
      throw NotImplementedError("no id for ${configurable::class.java}")
    }
    if (configurable is Configurable.Composite) {
      configurable.configurables.forEach { collectConfigurables(list, it) }
    }
  }
}