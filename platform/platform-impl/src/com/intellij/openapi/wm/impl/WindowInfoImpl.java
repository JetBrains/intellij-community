/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.wm.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class WindowInfoImpl implements Cloneable,JDOMExternalizable, WindowInfo {
  /**
   * XML tag.
   */
  @NonNls static final String TAG="window_info";
  /**
   * Default window weight.
   */
  static final float DEFAULT_WEIGHT= 0.33f;
  static final float DEFAULT_SIDE_WEIGHT = 0.5f;

  private boolean myActive;
  @NotNull
  private ToolWindowAnchor myAnchor;
  private boolean myAutoHide;
  /**
   * Bounds of window in "floating" mode. It equals to <code>null</code> if
   * floating bounds are undefined.
   */
  private Rectangle myFloatingBounds;
  private String myId;
  private ToolWindowType myInternalType;
  private ToolWindowType myType;
  private boolean myVisible;
  private boolean myShowStripeButton;
  private float myWeight;
  private float mySideWeight;
  private boolean mySplitMode;

  @NotNull private ToolWindowContentUiType myContentUiType = ToolWindowContentUiType.TABBED;
  /**
   * Defines order of tool window button inside the stripe.
   * The default value is <code>-1</code>.
   */
  private int myOrder;
  @NonNls static final String ID_ATTR = "id";
  @NonNls static final String ACTIVE_ATTR = "active";
  @NonNls static final String ANCHOR_ATTR = "anchor";
  @NonNls static final String AUTOHIDE_ATTR = "auto_hide";
  @NonNls static final String INTERNAL_TYPE_ATTR = "internal_type";
  @NonNls static final String TYPE_ATTR = "type";
  @NonNls static final String VISIBLE_ATTR = "visible";
  @NonNls static final String WEIGHT_ATTR = "weight";
  @NonNls static final String SIDE_WEIGHT_ATTR = "sideWeight";
  @NonNls static final String ORDER_ATTR = "order";
  @NonNls static final String X_ATTR = "x";
  @NonNls static final String Y_ATTR = "y";
  @NonNls static final String WIDTH_ATTR = "width";
  @NonNls static final String HEIGHT_ATTR = "height";
  @NonNls static final String SIDE_TOOL_ATTR = "side_tool";
  @NonNls static final String CONTENT_UI_ATTR = "content_ui";
  @NonNls static final String SHOW_STRIPE_BUTTON = "show_stripe_button";


  private boolean myWasRead;

  /**
   * Creates <code>WindowInfo</code> for tool window with specified <code>ID</code>.
   */
  WindowInfoImpl(@NotNull String id) {
    myActive = false;
    myAnchor = ToolWindowAnchor.LEFT;
    myAutoHide = false;
    myFloatingBounds = null;
    myId = id;
    setType(ToolWindowType.DOCKED);
    myVisible = false;
    myShowStripeButton = true;
    myWeight = DEFAULT_WEIGHT;
    mySideWeight = DEFAULT_SIDE_WEIGHT;
    myOrder = -1;
    mySplitMode = false;
  }

  /**
   * Creates copy of <code>WindowInfo</code> object.
   */
  @NotNull
  public WindowInfoImpl copy() {
    try {
      WindowInfoImpl info = (WindowInfoImpl)clone();
      if (myFloatingBounds != null) {
        info.myFloatingBounds = (Rectangle)myFloatingBounds.clone();
      }
      return info;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Copies all data from the passed <code>WindowInfo</code> into itself.
   */
  void copyFrom(final WindowInfoImpl info){
    myActive = info.myActive;
    myAnchor = info.myAnchor;
    myAutoHide = info.myAutoHide;
    myFloatingBounds = info.myFloatingBounds == null ? null : (Rectangle)info.myFloatingBounds.clone();
    myId = info.myId;
    setTypeAndCheck(info.myType);
    myInternalType = info.myInternalType;
    myVisible = info.myVisible;
    myWeight = info.myWeight;
    mySideWeight = info.mySideWeight;
    myOrder = info.myOrder;
    mySplitMode = info.mySplitMode;
    myContentUiType = info.myContentUiType;
  }

  /**
   * @return tool window's anchor in internal mode.
   */
  @NotNull
  @Override
  public ToolWindowAnchor getAnchor(){
    return myAnchor;
  }

  @NotNull
  @Override
  public ToolWindowContentUiType getContentUiType() {
    return myContentUiType;
  }

  void setContentUiType(@NotNull ToolWindowContentUiType type) {
    myContentUiType = type;
  }

  /**
   * @return bound of tool window in floating mode.
   */
  @Override
  public Rectangle getFloatingBounds(){
    return myFloatingBounds != null ? new Rectangle(myFloatingBounds) : null;
  }

  /**
   * @return <code>ID</code> of the tool window.
   */
  @NotNull
  String getId(){
    return myId;
  }

  /**
   * @return type of the tool window in internal (docked or sliding) mode. Actually the tool
   * window can be in floating mode, but this method has sense if you want to know what type
   * tool window had when it was internal one. The method never returns <code>null</code>.
   */
  ToolWindowType getInternalType(){
    return myInternalType;
  }

  /**
   * @return current type of tool window.
   * @see com.intellij.openapi.wm.ToolWindowType#DOCKED
   * @see com.intellij.openapi.wm.ToolWindowType#FLOATING
   * @see com.intellij.openapi.wm.ToolWindowType#SLIDING
   */
  @Override
  public ToolWindowType getType(){
    return myType;
  }

  /**
   * @return internal weight of tool window. "weigth" means how much of internal desktop
   * area the tool window is occupied. The weight has sense if the tool window is docked or
   * sliding.
   */
  float getWeight(){
    return myWeight;
  }

  float getSideWeight() {
    return mySideWeight;
  }

  public int getOrder(){
    return myOrder;
  }

  public void setOrder(final int order){
    myOrder=order;
  }

  @Override
  public boolean isActive(){
    return myActive;
  }

  @Override
  public boolean isAutoHide(){
    return myAutoHide;
  }

  @Override
  public boolean isDocked(){
    return ToolWindowType.DOCKED==myType;
  }

  @Override
  public boolean isFloating(){
    return ToolWindowType.FLOATING==myType;
  }

  @Override
  public boolean isWindowed(){
    return ToolWindowType.WINDOWED==myType;
  }

  @Override
  public boolean isSliding(){
    return ToolWindowType.SLIDING==myType;
  }

  boolean isVisible(){
    return myVisible;
  }

  public boolean isShowStripeButton() {
    return myShowStripeButton;
  }

  public void setShowStripeButton(boolean showStripeButton) {
    myShowStripeButton = showStripeButton;
  }

  @Override
  public boolean isSplit() {
    return mySplitMode;
  }

  public void setSplit(final boolean sideTool) {
    mySplitMode =sideTool;
  }

  @Override
  @SuppressWarnings({"EmptyCatchBlock"})
  public void readExternal(final Element element) {
    myId = element.getAttributeValue(ID_ATTR);
    myWasRead = true;
    try {
      myActive = Boolean.valueOf(element.getAttributeValue(ACTIVE_ATTR)).booleanValue();
    }
    catch (NumberFormatException ignored) {
    }
    try {
      myAnchor = ToolWindowAnchor.fromText(element.getAttributeValue(ANCHOR_ATTR));
    }
    catch (IllegalArgumentException ignored) {
    }
    myAutoHide = Boolean.valueOf(element.getAttributeValue(AUTOHIDE_ATTR)).booleanValue();
    try {
      myInternalType = ToolWindowType.valueOf(element.getAttributeValue(INTERNAL_TYPE_ATTR));
    }
    catch (IllegalArgumentException ignored) {
    }
    try {
      setTypeAndCheck(ToolWindowType.valueOf(element.getAttributeValue(TYPE_ATTR)));
    }
    catch (IllegalArgumentException ignored) {
    }
    myVisible = Boolean.valueOf(element.getAttributeValue(VISIBLE_ATTR)).booleanValue();
    if (element.getAttributeValue(SHOW_STRIPE_BUTTON) != null) {
      myShowStripeButton = Boolean.valueOf(element.getAttributeValue(SHOW_STRIPE_BUTTON)).booleanValue();
    }
    try {
      myWeight = Float.parseFloat(element.getAttributeValue(WEIGHT_ATTR));
    }
    catch (NumberFormatException ignored) {
    }
    try {
      String value = element.getAttributeValue(SIDE_WEIGHT_ATTR);
      if (value != null) {
        mySideWeight = Float.parseFloat(value);
      }
    }
    catch (NumberFormatException ignored) {
    }
    try {
      myOrder = Integer.valueOf(element.getAttributeValue(ORDER_ATTR)).intValue();
    }
    catch (NumberFormatException ignored) {
    }
    try {
      int x = Integer.parseInt(element.getAttributeValue(X_ATTR));
      int y = Integer.parseInt(element.getAttributeValue(Y_ATTR));
      int width = Integer.parseInt(element.getAttributeValue(WIDTH_ATTR));
      int height = Integer.parseInt(element.getAttributeValue(HEIGHT_ATTR));
      myFloatingBounds = new Rectangle(x, y, width, height);
    }
    catch (NumberFormatException ignored) {
    }
    mySplitMode = Boolean.parseBoolean(element.getAttributeValue(SIDE_TOOL_ATTR));

    myContentUiType = ToolWindowContentUiType.getInstance(element.getAttributeValue(CONTENT_UI_ATTR));
  }

  /**
   * Sets new anchor.
   */
  void setAnchor(@NotNull final ToolWindowAnchor anchor){
    myAnchor=anchor;
  }

  void setActive(final boolean active){
    myActive=active;
  }

  void setAutoHide(final boolean autoHide){
    myAutoHide=autoHide;
  }

  void setFloatingBounds(final Rectangle floatingBounds){
    myFloatingBounds=floatingBounds;
  }

  void setType(@NotNull final ToolWindowType type){
    if(ToolWindowType.DOCKED==type||ToolWindowType.SLIDING==type){
      myInternalType=type;
    }
    setTypeAndCheck(type);
  }
  //Hardcoded to avoid single-usage-API
  private void setTypeAndCheck(ToolWindowType type) {
    myType = ToolWindowId.PREVIEW == myId && type == ToolWindowType.DOCKED ? ToolWindowType.SLIDING : type;
  }

  void setVisible(final boolean visible){
    myVisible=visible;
  }

  /**
   * Sets window weight and adjust it to [0..1] range if necessary.
   */
  void setWeight(float weight){
    myWeight = Math.max(0, Math.min(1, weight));
  }

  void setSideWeight(float weight){
    mySideWeight = Math.max(0, Math.min(1, weight));
  }

  @Override
  public void writeExternal(final Element element){
    element.setAttribute(ID_ATTR,myId);
    element.setAttribute(ACTIVE_ATTR, Boolean.toString(myActive));
    element.setAttribute(ANCHOR_ATTR,myAnchor.toString());
    element.setAttribute(AUTOHIDE_ATTR, Boolean.toString(myAutoHide));
    element.setAttribute(INTERNAL_TYPE_ATTR,myInternalType.toString());
    element.setAttribute(TYPE_ATTR,myType.toString());
    element.setAttribute(VISIBLE_ATTR, Boolean.toString(myVisible));
    element.setAttribute(SHOW_STRIPE_BUTTON, Boolean.toString(myShowStripeButton));
    element.setAttribute(WEIGHT_ATTR,Float.toString(myWeight));
    element.setAttribute(SIDE_WEIGHT_ATTR, Float.toString(mySideWeight));
    element.setAttribute(ORDER_ATTR,Integer.toString(myOrder));
    element.setAttribute(SIDE_TOOL_ATTR, Boolean.toString(mySplitMode));
    element.setAttribute(CONTENT_UI_ATTR, myContentUiType.getName());
    if(myFloatingBounds!=null){
      element.setAttribute(X_ATTR,Integer.toString(myFloatingBounds.x));
      element.setAttribute(Y_ATTR,Integer.toString(myFloatingBounds.y));
      element.setAttribute(WIDTH_ATTR,Integer.toString(myFloatingBounds.width));
      element.setAttribute(HEIGHT_ATTR,Integer.toString(myFloatingBounds.height));
    }
  }

  public boolean equals(final Object obj){
    if(!(obj instanceof WindowInfoImpl)){
      return false;
    }
    final WindowInfoImpl info=(WindowInfoImpl)obj;
    return myActive == info.myActive &&
           myAnchor == info.myAnchor &&
           myId.equals(info.myId) &&
           myAutoHide == info.myAutoHide &&
           Comparing.equal(myFloatingBounds, info.myFloatingBounds) &&
           myInternalType == info.myInternalType &&
           myType == info.myType &&
           myVisible == info.myVisible &&
           myShowStripeButton == info.myShowStripeButton &&
           myWeight == info.myWeight &&
           mySideWeight == info.mySideWeight &&
           myOrder == info.myOrder &&
           mySplitMode == info.mySplitMode &&
          myContentUiType == info.myContentUiType;
  }

  public int hashCode(){
    return myAnchor.hashCode()+myId.hashCode()+myType.hashCode()+myOrder;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString(){
    return getClass().getName() + "[myId=" + myId
           + "; myVisible=" + myVisible
           + "; myShowStripeButton=" + myShowStripeButton
           + "; myActive=" + myActive
           + "; myAnchor=" + myAnchor
           + "; myOrder=" + myOrder
           + "; myAutoHide=" + myAutoHide
           + "; myWeight=" + myWeight
           + "; mySideWeight=" + mySideWeight
           + "; myType=" + myType
           + "; myInternalType=" + myInternalType
           + "; myFloatingBounds=" + myFloatingBounds
           + "; mySplitMode=" + mySplitMode
           + "; myContentUiType=" + myContentUiType.getName() +
           ']';
  }

  public boolean wasRead() {
    return myWasRead;
  }
}
