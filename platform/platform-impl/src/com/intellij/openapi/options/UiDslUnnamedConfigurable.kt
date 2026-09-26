// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel

/**
 * Use this interface only if the configurable can be a part of composite configurable ([BoundCompositeConfigurable] for now).
 * Otherwise, consider using [DslConfigurableBase] or its descendants
 */
interface UiDslUnnamedConfigurable : UnnamedConfigurable {

  /**
   * Creates content of configurable as part of another configurable. Rules:
   *
   * * To avoid interference with parent grid and its content use [Panel.group] or [Panel.panel] as root element
   * * There are two use-cases that should be supported:
   *   1. [createComponent] and [apply]/[isModified]/[reset] methods
   *   2. [createContent] integrated bindings and various apply/modified/reset hooks, which are provided by Kotlin UI DSL framework.
   *   In this case [apply]/[isModified]/[reset] methods of this configurable are not invoked
   */
  fun Panel.createContent()

  /**
   * Methods [isModified], [reset] and [apply] are final because they are never called for [UiDslUnnamedConfigurable]
   * when the configurable is a part of composite configurable
   */
  abstract class Simple : DslConfigurableBase(), UiDslUnnamedConfigurable {

    final override fun createPanel(): DialogPanel {
      return panel {
        createContent()
      }
    }

    final override fun isModified(): Boolean = super.isModified()
    final override fun reset(): Unit = super<DslConfigurableBase>.reset()
    final override fun apply(): Unit = super.apply()
  }
}
