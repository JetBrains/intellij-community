// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

@ApiStatus.Internal
internal sealed class LcrCellBaseImpl<T : LcrInitParams>(val initParams: T, val baselineAlign: Boolean, val beforeGap: LcrRow.Gap) {

  enum class Type(private val instanceFactory: () -> JComponent) {
    ICON(::JLabel),
    SIMPLE_COLORED_TEXT(::PatchedSimpleColoredComponent),
    SWITCH(::OnOffButton);

    private val instance = lazy { instanceFactory() }

    fun createInstance(): JComponent {
      return instanceFactory()
    }

    fun isInstance(component: JComponent): Boolean {
      return component::class.java == instance.value::class.java
    }
  }

  abstract val type: Type

  abstract fun apply(component: JComponent, enabled: Boolean, list: JList<*>, isSelected: Boolean)
}

internal class PatchedSimpleColoredComponent : SimpleColoredComponent() {

  var renderingHints: Map<RenderingHints.Key, Any?>? = null

  override fun applyAdditionalHints(g: Graphics2D) {
    super.applyAdditionalHints(g)

    renderingHints?.let {
      g.addRenderingHints(it)
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = PatchedAccessibleSimpleColoredComponent()
    }
    return accessibleContext
  }

  init {
    @Suppress("UseDPIAwareInsets")
    ipad = Insets(ipad.top, 0, ipad.bottom, 0)
    myBorder = null
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
