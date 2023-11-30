// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.style

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.awt.Color
import java.awt.Insets
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.border.Border

internal const val HOVER_KEY: String = "STYLE_HOVER"
private val LOG: Logger
  get() = logger<StyleProperty>()

internal sealed class StyleProperty(
  private val setProperty: (JComponent, Any?) -> Unit,
  private val getProperty: (JComponent) -> Any?,
  private val valueType: Class<out Any?>,
  private val componentType: Class<out Any?> = JComponent::class.java
) {
  companion object {
    fun getPropertySnapshot(component: JComponent): Properties {
      val base = Properties()
      for (p in arrayOf(FOREGROUND, BACKGROUND, OPAQUE, BORDER, ICON, MARGIN)) {
        if (p.componentType.isInstance(component))
          base.setValue(p, p.getProperty(component))
      }
      return base
    }
  }

  object OPAQUE : StyleProperty(
    { component, isOpaque -> component.isOpaque = if (isOpaque == null) true else isOpaque as Boolean },
    { component -> component.isOpaque },
    Boolean::class.java
  )

  object BACKGROUND : StyleProperty(
    { component, background -> component.background = background as Color? },
    { component -> component.background },
    Color::class.java
  )

  object HOVER : StyleProperty(
    { component, background -> component.putClientProperty(HOVER_KEY, background as Color?) },
    { component -> component.getClientProperty(HOVER_KEY) },
    Color::class.java
  )

  object FOREGROUND : StyleProperty(
    { component, foreground -> component.foreground = foreground as Color? },
    { component -> component.foreground },
    Color::class.java
  )

  object BORDER : StyleProperty(
    { component, border -> component.border = border as Border? },
    { component -> component.border },
    Border::class.java
  )

  object ICON : StyleProperty(
    { component, icon -> (component as AbstractButton).icon = icon as Icon? },
    { component -> (component as AbstractButton).icon },
    Icon::class.java,
    AbstractButton::class.java
  )

  object MARGIN : StyleProperty(
    { component, margin -> (component as AbstractButton).margin = margin as Insets? },
    { component -> (component as AbstractButton).margin },
    Insets::class.java,
    AbstractButton::class.java
  )

  private fun checkTypes(component: JComponent, value: Any?): Boolean {
    if (!componentType.isInstance(component)) {
      LOG.warn("${javaClass.canonicalName} Incorrect class type: ${component.javaClass.canonicalName} instead of ${componentType.canonicalName}")
      return false
    }
    if (valueType == Boolean::class.java) {
      return (value == true || value == false)
    }
    if (!(value == null || valueType.isInstance(value))) {
      LOG.warn("${javaClass.canonicalName} Incorrect value type: ${value.javaClass.canonicalName} instead of ${valueType.canonicalName}")
      return false
    }
    return true
  }

  fun apply(component: JComponent, value: Any?) {
    if (!checkTypes(component, value)) {
      return
    }
    setProperty(component, value)
  }
}