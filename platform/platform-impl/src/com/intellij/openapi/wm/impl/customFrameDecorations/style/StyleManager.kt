// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.style

import javax.swing.JComponent

internal class StyleManager {
  companion object {
    fun <T : JComponent> applyStyle(component: T, style: ComponentStyle<T>) {
      removeStyle(component)
      style.applyStyle(component)
    }

    fun <T : JComponent> removeStyle(component: T) {
      val propertyChangeListeners = component.getPropertyChangeListeners(ComponentStyle.ENABLED_PROPERTY)

      for (styleComponentListener in propertyChangeListeners) {
        if(styleComponentListener is ComponentStyle.StyleComponentListener<*>) {
          styleComponentListener.destroy()
        }
      }
    }
  }
}

