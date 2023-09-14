// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.ListCellRenderer

interface UiDslRendererProvider {
  companion object {
    fun <T> getRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
      return EP.extensions.first { it.isApplicable() }.getRenderer(renderContent)
    }

    @JvmStatic
    val EP = ExtensionPointName<UiDslRendererProvider>("com.intellij.uiDslRendererProvider")
  }

  fun isApplicable(): Boolean
  fun <T> getRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T>
}