// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal sealed class LcrCellBaseImpl<T: LcrInitParams>(val initParams: T, val baselineAlign: Boolean, val gapBefore: LcrRow.Gap) {

  enum class Type(private val instanceFactory: () -> JComponent) {
    ICON(::JLabel),
    TEXT(::JLabel),
    SIMPLE_COLORED_TEXT(::PatchedSimpleColoredComponent);

    private val instance = lazy { instanceFactory() }

    fun createInstance(): JComponent {
      return instanceFactory()
    }

    fun isInstance(component: JComponent): Boolean {
      return component::class.java == instance.value::class.java
    }
  }

  abstract val type: Type

  abstract fun apply(component: JComponent)
}

private class PatchedSimpleColoredComponent : SimpleColoredComponent() {

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = PatchedAccessibleSimpleColoredComponent()
    }
    return accessibleContext
  }

  init {
    @Suppress("UseDPIAwareInsets")
    ipad = Insets(ipad.top, 0, ipad.bottom, 0)
    isOpaque = false
  }

  /**
   * Allows to set/reset custom accessibleName
   */
  private inner class PatchedAccessibleSimpleColoredComponent : AccessibleSimpleColoredComponent() {
    override fun getAccessibleName(): String? {
      return accessibleName
    }
  }
}
