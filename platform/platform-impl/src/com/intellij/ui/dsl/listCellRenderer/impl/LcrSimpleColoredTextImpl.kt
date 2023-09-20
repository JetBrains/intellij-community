// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Insets
import javax.accessibility.AccessibleContext

@ApiStatus.Internal
internal class LcrSimpleColoredTextImpl : LcrCellBaseImpl() {

  override val component = object : SimpleColoredComponent() {

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
    private inner class PatchedAccessibleSimpleColoredComponent: AccessibleSimpleColoredComponent() {
      override fun getAccessibleName(): String? {
        return accessibleName
      }
    }
  }

  fun init(text: @Nls String, initParams: LcrTextInitParamsImpl, selected: Boolean, rowForeground: Color) {
    component.clear()
    component.font = initParams.font
    val attributes = if (selected) SimpleTextAttributes(initParams.attributes!!.style, rowForeground) else initParams.attributes!!
    component.append(text, attributes)
    component.accessibleContext.accessibleName = initParams.accessibleName
  }
}
