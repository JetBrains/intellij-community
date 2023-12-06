// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.dsl.listCellRenderer.impl.LcrRowImpl
import javax.swing.ListCellRenderer

/**
 * Provides cell renderer for DSL UI lists (@see [com.intellij.ui.dsl.listCellRenderer.listCellRenderer]
 *
 * Default DSL renderer ([LcrRowImpl]) will be chosen if there are no other suitable renderer providers (@see [isApplicable])
 */
interface UiDslRendererProvider {
  companion object {
    fun <T> getRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
      val renderer = EP.findFirstSafe { it.isApplicable() }?.getRenderer(renderContent)
      return renderer ?: LcrRowImpl(renderContent)
    }

    @JvmStatic
    val EP = ExtensionPointName<UiDslRendererProvider>("com.intellij.uiDslRendererProvider")
  }

  /**
   * Checks if the current renderer provider is applicable for the specified situation
   *
   * @return true if the provider is able to return its renderer ([getRenderer])
   */
  fun isApplicable(): Boolean

  /**
   * Retrieves a `ListCellRenderer` for rendering the content of an `LcrRow`.
   *
   * @param renderContent A lambda expression representing a function that takes an `LcrRow` and returns `Unit`.
   *                      This function is responsible for providing the content of the row.
   * @return The `ListCellRenderer` to be used for rendering the content of an `LcrRow`.
   */
  fun <T> getRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T>
}