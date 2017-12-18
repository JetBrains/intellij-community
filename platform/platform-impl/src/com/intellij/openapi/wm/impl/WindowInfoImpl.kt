// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.*
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.awt.Rectangle

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class WindowInfoImpl
/**
 * Creates `WindowInfo` for tool window with specified `ID`.
 */
internal constructor(id: String) : Cloneable, JDOMExternalizable, WindowInfo {

  private var myActive: Boolean = false
  private var myAnchor = ToolWindowAnchor.LEFT
  private var myAutoHide: Boolean = false
  /**
   * Bounds of window in "floating" mode. It equals to `null` if
   * floating bounds are undefined.
   */
  private var myFloatingBounds: Rectangle? = null
  /**
   * @return `ID` of the tool window.
   */
  internal var id: String? = null
    private set
  /**
   * @return type of the tool window in internal (docked or sliding) mode. Actually the tool
   * window can be in floating mode, but this method has sense if you want to know what type
   * tool window had when it was internal one. The method never returns `null`.
   */
  internal var internalType: ToolWindowType? = null
    private set
  private var myType: ToolWindowType? = null
  internal var isVisible: Boolean = false
  private var myShowStripeButton = true
  private var myWeight = DEFAULT_WEIGHT
  private var mySideWeight = DEFAULT_SIDE_WEIGHT
  private var mySplitMode: Boolean = false

  private var myContentUiType = ToolWindowContentUiType.TABBED
  /**
   * Defines order of tool window button inside the stripe.
   * The default value is `-1`.
   */
  var order = -1


  private var myWasRead: Boolean = false

  /**
   * @return internal weight of tool window. "weigth" means how much of internal desktop
   * area the tool window is occupied. The weight has sense if the tool window is docked or
   * sliding.
   */
  /**
   * Sets window weight and adjust it to [0..1] range if necessary.
   */
  internal var weight: Float
    get() = myWeight
    set(weight) {
      myWeight = Math.max(0f, Math.min(1f, weight))
    }

  internal var sideWeight: Float
    get() = mySideWeight
    set(weight) {
      mySideWeight = Math.max(0f, Math.min(1f, weight))
    }

  init {
    this.id = id
    setType(ToolWindowType.DOCKED)
  }

  /**
   * Creates copy of `WindowInfo` object.
   */
  fun copy(): WindowInfoImpl {
    try {
      val info = clone() as WindowInfoImpl
      if (myFloatingBounds != null) {
        info.myFloatingBounds = myFloatingBounds!!.clone() as Rectangle
      }
      return info
    }
    catch (e: CloneNotSupportedException) {
      throw RuntimeException(e)
    }

  }

  /**
   * Copies all data from the passed `WindowInfo` into itself.
   */
  internal fun copyFrom(info: WindowInfoImpl) {
    myActive = info.myActive
    myAnchor = info.myAnchor
    myAutoHide = info.myAutoHide
    myFloatingBounds = if (info.myFloatingBounds == null) null else info.myFloatingBounds!!.clone() as Rectangle
    id = info.id
    setTypeAndCheck(info.myType!!)
    internalType = info.internalType
    isVisible = info.isVisible
    myWeight = info.myWeight
    mySideWeight = info.mySideWeight
    order = info.order
    mySplitMode = info.mySplitMode
    myContentUiType = info.myContentUiType
  }

  /**
   * @return tool window's anchor in internal mode.
   */
  override fun getAnchor(): ToolWindowAnchor {
    return myAnchor
  }

  override fun getContentUiType(): ToolWindowContentUiType {
    return myContentUiType
  }

  internal fun setContentUiType(type: ToolWindowContentUiType) {
    myContentUiType = type
  }

  /**
   * @return bound of tool window in floating mode.
   */
  override fun getFloatingBounds(): Rectangle? {
    return if (myFloatingBounds != null) Rectangle(myFloatingBounds!!) else null
  }

  /**
   * @return current type of tool window.
   * @see ToolWindowType.DOCKED
   *
   * @see ToolWindowType.FLOATING
   *
   * @see ToolWindowType.SLIDING
   */
  override fun getType(): ToolWindowType? {
    return myType
  }

  override fun isActive(): Boolean {
    return myActive
  }

  override fun isAutoHide(): Boolean {
    return myAutoHide
  }

  override fun isDocked(): Boolean {
    return ToolWindowType.DOCKED == myType
  }

  override fun isFloating(): Boolean {
    return ToolWindowType.FLOATING == myType
  }

  override fun isWindowed(): Boolean {
    return ToolWindowType.WINDOWED == myType
  }

  override fun isSliding(): Boolean {
    return ToolWindowType.SLIDING == myType
  }

  override fun isShowStripeButton(): Boolean {
    return myShowStripeButton
  }

  internal fun setShowStripeButton(showStripeButton: Boolean) {
    myShowStripeButton = showStripeButton
  }

  override fun isSplit(): Boolean {
    return mySplitMode
  }

  fun setSplit(sideTool: Boolean) {
    mySplitMode = sideTool
  }

  override fun readExternal(element: Element) {
    id = element.getAttributeValue(ID_ATTR)
    myWasRead = true
    myActive = java.lang.Boolean.parseBoolean(element.getAttributeValue(ACTIVE_ATTR)) && canActivateOnStart(id)
    try {
      myAnchor = ToolWindowAnchor.fromText(element.getAttributeValue(ANCHOR_ATTR))
    }
    catch (ignored: IllegalArgumentException) {
    }

    myAutoHide = java.lang.Boolean.parseBoolean(element.getAttributeValue(AUTOHIDE_ATTR))
    try {
      internalType = ToolWindowType.valueOf(element.getAttributeValue(INTERNAL_TYPE_ATTR))
    }
    catch (ignored: IllegalArgumentException) {
    }

    try {
      setTypeAndCheck(ToolWindowType.valueOf(element.getAttributeValue(TYPE_ATTR)))
    }
    catch (ignored: IllegalArgumentException) {
    }

    isVisible = java.lang.Boolean.parseBoolean(element.getAttributeValue(VISIBLE_ATTR)) && canActivateOnStart(id)
    myShowStripeButton = java.lang.Boolean.parseBoolean(element.getAttributeValue(SHOW_STRIPE_BUTTON, "true"))
    try {
      myWeight = java.lang.Float.parseFloat(element.getAttributeValue(WEIGHT_ATTR)!!)
    }
    catch (ignored: NumberFormatException) {
    }

    try {
      val value = element.getAttributeValue(SIDE_WEIGHT_ATTR)
      if (value != null) {
        mySideWeight = java.lang.Float.parseFloat(value)
      }
    }
    catch (ignored: NumberFormatException) {
      mySideWeight = DEFAULT_SIDE_WEIGHT
    }

    order = StringUtilRt.parseInt(element.getAttributeValue(ORDER_ATTR), order)
    myFloatingBounds = deserializeBounds(element)
    mySplitMode = java.lang.Boolean.parseBoolean(element.getAttributeValue(SIDE_TOOL_ATTR))

    myContentUiType = ToolWindowContentUiType.getInstance(element.getAttributeValue(CONTENT_UI_ATTR))
  }

  /**
   * Sets new anchor.
   */
  internal fun setAnchor(anchor: ToolWindowAnchor) {
    myAnchor = anchor
  }

  internal fun setActive(active: Boolean) {
    myActive = active
  }

  internal fun setAutoHide(autoHide: Boolean) {
    myAutoHide = autoHide
  }

  internal fun setFloatingBounds(floatingBounds: Rectangle) {
    myFloatingBounds = floatingBounds
  }

  internal fun setType(type: ToolWindowType) {
    if (ToolWindowType.DOCKED == type || ToolWindowType.SLIDING == type) {
      internalType = type
    }
    setTypeAndCheck(type)
  }

  //Hardcoded to avoid single-usage-API
  private fun setTypeAndCheck(type: ToolWindowType) {
    myType = if (ToolWindowId.PREVIEW === id && type == ToolWindowType.DOCKED) ToolWindowType.SLIDING else type
  }

  override fun writeExternal(element: Element) {
    element.setAttribute(ID_ATTR, id!!)

    if (myActive) {
      element.setAttribute(ACTIVE_ATTR, java.lang.Boolean.toString(true))
    }

    element.setAttribute(ANCHOR_ATTR, myAnchor.toString())
    if (myAutoHide) {
      element.setAttribute(AUTOHIDE_ATTR, java.lang.Boolean.toString(true))
    }
    element.setAttribute(INTERNAL_TYPE_ATTR, internalType!!.toString())
    element.setAttribute(TYPE_ATTR, myType!!.toString())
    element.setAttribute(VISIBLE_ATTR, java.lang.Boolean.toString(isVisible))
    if (!myShowStripeButton) {
      element.setAttribute(SHOW_STRIPE_BUTTON, java.lang.Boolean.toString(false))
    }
    element.setAttribute(WEIGHT_ATTR, java.lang.Float.toString(myWeight))

    if (mySideWeight != DEFAULT_SIDE_WEIGHT) {
      element.setAttribute(SIDE_WEIGHT_ATTR, java.lang.Float.toString(mySideWeight))
    }
    element.setAttribute(ORDER_ATTR, Integer.toString(order))
    element.setAttribute(SIDE_TOOL_ATTR, java.lang.Boolean.toString(mySplitMode))
    element.setAttribute(CONTENT_UI_ATTR, myContentUiType.name)

    if (myFloatingBounds != null) {
      serializeBounds(myFloatingBounds!!, element)
    }
  }

  override fun equals(obj: Any?): Boolean {
    if (obj !is WindowInfoImpl) {
      return false
    }

    val info = obj as WindowInfoImpl?
    return myActive == info!!.myActive &&
           myAnchor == info.myAnchor &&
           id == info.id &&
           myAutoHide == info.myAutoHide &&
           Comparing.equal(myFloatingBounds, info.myFloatingBounds) &&
           internalType == info.internalType &&
           myType == info.myType &&
           isVisible == info.isVisible &&
           myShowStripeButton == info.myShowStripeButton &&
           myWeight == info.myWeight &&
           mySideWeight == info.mySideWeight &&
           order == info.order &&
           mySplitMode == info.mySplitMode &&
           myContentUiType === info.myContentUiType
  }

  override fun hashCode(): Int {
    return myAnchor.hashCode() + id!!.hashCode() + myType!!.hashCode() + order
  }

  override fun toString(): String {
    return (javaClass.name + "[myId=" + id
            + "; myVisible=" + isVisible
            + "; myShowStripeButton=" + myShowStripeButton
            + "; myActive=" + myActive
            + "; myAnchor=" + myAnchor
            + "; myOrder=" + order
            + "; myAutoHide=" + myAutoHide
            + "; myWeight=" + myWeight
            + "; mySideWeight=" + mySideWeight
            + "; myType=" + myType
            + "; myInternalType=" + internalType
            + "; myFloatingBounds=" + myFloatingBounds
            + "; mySplitMode=" + mySplitMode
            + "; myContentUiType=" + myContentUiType.name +
            ']')
  }

  internal fun wasRead(): Boolean {
    return myWasRead
  }

  companion object {
    /**
     * XML tag.
     */
    @NonNls internal val TAG = "window_info"
    /**
     * Default window weight.
     */
    internal val DEFAULT_WEIGHT = 0.33f
    private val DEFAULT_SIDE_WEIGHT = 0.5f
    @NonNls private val ID_ATTR = "id"
    @NonNls private val ACTIVE_ATTR = "active"
    @NonNls private val ANCHOR_ATTR = "anchor"
    @NonNls private val AUTOHIDE_ATTR = "auto_hide"
    @NonNls private val INTERNAL_TYPE_ATTR = "internal_type"
    @NonNls private val TYPE_ATTR = "type"
    @NonNls private val VISIBLE_ATTR = "visible"
    @NonNls private val WEIGHT_ATTR = "weight"
    @NonNls private val SIDE_WEIGHT_ATTR = "sideWeight"
    @NonNls private val ORDER_ATTR = "order"
    @NonNls private val SIDE_TOOL_ATTR = "side_tool"
    @NonNls private val CONTENT_UI_ATTR = "content_ui"
    @NonNls private val SHOW_STRIPE_BUTTON = "show_stripe_button"

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
}
