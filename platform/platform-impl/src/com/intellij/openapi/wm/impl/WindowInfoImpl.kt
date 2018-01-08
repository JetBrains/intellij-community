/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.*
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.jdom.Element
import java.awt.Rectangle

private val LOG = logger<WindowInfoImpl>()

@Suppress("EqualsOrHashCode")
@Tag("window_info")
@Property(style = Property.Style.ATTRIBUTE)
class WindowInfoImpl : Cloneable, WindowInfo, BaseState() {
  companion object {
    @JvmField
    internal val TAG = "window_info"
    @JvmField
    val DEFAULT_WEIGHT = 0.33f

    private fun canActivateOnStart(id: String?): Boolean {
      for (ep in ToolWindowEP.EP_NAME.extensions) {
        if (id == ep.id) {
          val factory = ep.toolWindowFactory
          return !factory!!.isDoNotActivateOnStart
        }
      }
      return true
    }
  }

  override var isActive by property(false)

  @get:Attribute(converter = ToolWindowAnchorConverter::class)
  override var anchor: ToolWindowAnchor by property(ToolWindowAnchor.LEFT) { it == ToolWindowAnchor.LEFT }

  @get:Attribute("auto_hide")
  override var isAutoHide by property(false)

  /**
   * Bounds of window in "floating" mode. It equals to `null` if
   * floating bounds are undefined.
   */
  @get:Property(flat = true, style = Property.Style.ATTRIBUTE)
  override var floatingBounds by property<Rectangle?>()

  /**
   * @return `ID` of the tool window
   */
  var id by string()

  /**
   * @return type of the tool window in internal (docked or sliding) mode. Actually the tool
   * window can be in floating mode, but this method has sense if you want to know what type
   * tool window had when it was internal one.
   */
  @get:Attribute("internal_type")
  var internalType by property(ToolWindowType.DOCKED)

  override var type by property(ToolWindowType.DOCKED)

  @get:Attribute("visible")
  var isVisible by property(false)

  @get:Attribute("show_stripe_button")
  override var isShowStripeButton by property(true)

  /**
   * Internal weight of tool window. "weight" means how much of internal desktop
   * area the tool window is occupied. The weight has sense if the tool window is docked or
   * sliding.
   */
  var weight by property(DEFAULT_WEIGHT) { Math.max(0f, Math.min(1f, it)) }

  var sideWeight by property(0.5f) { Math.max(0f, Math.min(1f, it)) }

  @get:Attribute("side_tool")
  override var isSplit by property(false)

  @get:Attribute("content_ui", converter = ContentUiTypeConverter::class)
  override var contentUiType: ToolWindowContentUiType by property(ToolWindowContentUiType.TABBED, { it == ToolWindowContentUiType.TABBED })

  /**
   * Defines order of tool window button inside the stripe.
   */
  var order by property(-1)

  private var wasRead: Boolean = false

  init {
    this.id = id
  }

  fun copy(): WindowInfoImpl {
    val info = WindowInfoImpl()
    info.copyFrom(this)
    return info
  }

  override val isDocked: Boolean
    get() = type == ToolWindowType.DOCKED

  override val isFloating: Boolean
    get() = type == ToolWindowType.FLOATING

  override val isWindowed: Boolean
    get() = type == ToolWindowType.WINDOWED

  override val isSliding: Boolean
    get() = type == ToolWindowType.SLIDING

  fun readExternal(element: Element) {
    wasRead = true

    try {
      setTypeAndCheck(ToolWindowType.valueOf(element.getAttributeValue("type")))
    }
    catch (ignored: IllegalArgumentException) {
    }

    if (isVisible && !canActivateOnStart(id)) {
      isVisible = false
    }
  }

  internal fun setType(type: ToolWindowType) {
    if (ToolWindowType.DOCKED == type || ToolWindowType.SLIDING == type) {
      internalType = type
    }
    setTypeAndCheck(type)
  }

  //Hardcoded to avoid single-usage-API
  private fun setTypeAndCheck(value: ToolWindowType) {
    type = if (ToolWindowId.PREVIEW === id && value == ToolWindowType.DOCKED) ToolWindowType.SLIDING else value
  }

  override fun hashCode(): Int {
    return anchor.hashCode() + id!!.hashCode() + type.hashCode() + order
  }

  fun wasRead() = wasRead

  override fun toString(): String {
    return "id: $id, ${super.toString()}"
  }
}

private class ContentUiTypeConverter : Converter<ToolWindowContentUiType>() {
  override fun fromString(value: String): ToolWindowContentUiType = ToolWindowContentUiType.getInstance(value)

  override fun toString(value: ToolWindowContentUiType): String  = value.name
}

private class ToolWindowAnchorConverter : Converter<ToolWindowAnchor>() {
  override fun fromString(value: String): ToolWindowAnchor {
    try {
      return ToolWindowAnchor.fromText(value)
    }
    catch (e: IllegalArgumentException) {
      LOG.warn(e)
      return ToolWindowAnchor.LEFT
    }
  }

  override fun toString(value: ToolWindowAnchor): String  = value.toString()
}