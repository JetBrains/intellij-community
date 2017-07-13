/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
public final class WindowInfoImpl implements Cloneable, JDOMExternalizable, WindowInfo {
  /**
   * XML tag.
   */
  @NonNls static final String TAG = "window_info";
  /**
   * Default window weight.
   */
  static final float DEFAULT_WEIGHT = 0.33f;
  private static final float DEFAULT_SIDE_WEIGHT = 0.5f;

  private boolean myActive;
  @NotNull
  private ToolWindowAnchor myAnchor = ToolWindowAnchor.LEFT;
  private boolean myAutoHide;
  /**
   * Bounds of window in "floating" mode. It equals to {@code null} if
   * floating bounds are undefined.
   */
  private Rectangle myFloatingBounds;
  private String myId;
  private ToolWindowType myInternalType;
  private ToolWindowType myType;
  private boolean myVisible;
  private boolean myShowStripeButton = true;
  private float myWeight = DEFAULT_WEIGHT;
  private float mySideWeight = DEFAULT_SIDE_WEIGHT;
  private boolean mySplitMode;

  @NotNull private ToolWindowContentUiType myContentUiType = ToolWindowContentUiType.TABBED;
  /**
   * Defines order of tool window button inside the stripe.
   * The default value is {@code -1}.
   */
  private int myOrder = -1;
  @NonNls private static final String ID_ATTR = "id";
  @NonNls private static final String ACTIVE_ATTR = "active";
  @NonNls private static final String ANCHOR_ATTR = "anchor";
  @NonNls private static final String AUTOHIDE_ATTR = "auto_hide";
  @NonNls private static final String INTERNAL_TYPE_ATTR = "internal_type";
  @NonNls private static final String TYPE_ATTR = "type";
  @NonNls private static final String VISIBLE_ATTR = "visible";
  @NonNls private static final String WEIGHT_ATTR = "weight";
  @NonNls private static final String SIDE_WEIGHT_ATTR = "sideWeight";
  @NonNls private static final String ORDER_ATTR = "order";
  @NonNls private static final String SIDE_TOOL_ATTR = "side_tool";
  @NonNls private static final String CONTENT_UI_ATTR = "content_ui";
  @NonNls private static final String SHOW_STRIPE_BUTTON = "show_stripe_button";


  private boolean myWasRead;

  /**
   * Creates {@code WindowInfo} for tool window with specified {@code ID}.
   */
  WindowInfoImpl(@NotNull String id) {
    myId = id;
    setType(ToolWindowType.DOCKED);
  }

  /**
   * Creates copy of {@code WindowInfo} object.
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
   * Copies all data from the passed {@code WindowInfo} into itself.
   */
  void copyFrom(@NotNull WindowInfoImpl info){
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
   * @return {@code ID} of the tool window.
   */
  @NotNull
  String getId(){
    return myId;
  }

  /**
   * @return type of the tool window in internal (docked or sliding) mode. Actually the tool
   * window can be in floating mode, but this method has sense if you want to know what type
   * tool window had when it was internal one. The method never returns {@code null}.
   */
  @NotNull
  ToolWindowType getInternalType(){
    return myInternalType;
  }

  /**
   * @return current type of tool window.
   * @see ToolWindowType#DOCKED
   * @see ToolWindowType#FLOATING
   * @see ToolWindowType#SLIDING
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

  @Override
  public boolean isShowStripeButton() {
    return myShowStripeButton;
  }

  void setShowStripeButton(boolean showStripeButton) {
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
  @SuppressWarnings("EmptyCatchBlock")
  public void readExternal(final Element element) {
    myId = element.getAttributeValue(ID_ATTR);
    myWasRead = true;
    myActive = Boolean.parseBoolean(element.getAttributeValue(ACTIVE_ATTR)) && canActivateOnStart(myId);
    try {
      myAnchor = ToolWindowAnchor.fromText(element.getAttributeValue(ANCHOR_ATTR));
    }
    catch (IllegalArgumentException ignored) {
    }
    myAutoHide = Boolean.parseBoolean(element.getAttributeValue(AUTOHIDE_ATTR));
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
    myVisible = Boolean.parseBoolean(element.getAttributeValue(VISIBLE_ATTR)) && canActivateOnStart(myId);
    if (element.getAttributeValue(SHOW_STRIPE_BUTTON) != null) {
      myShowStripeButton = Boolean.parseBoolean(element.getAttributeValue(SHOW_STRIPE_BUTTON));
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
      myOrder = Integer.parseInt(element.getAttributeValue(ORDER_ATTR));
    }
    catch (NumberFormatException ignored) {
    }
    myFloatingBounds = ProjectFrameBoundsKt.deserializeBounds(element);
    mySplitMode = Boolean.parseBoolean(element.getAttributeValue(SIDE_TOOL_ATTR));

    myContentUiType = ToolWindowContentUiType.getInstance(element.getAttributeValue(CONTENT_UI_ATTR));
  }

  private static boolean canActivateOnStart(String id) {
    for (ToolWindowEP ep : ToolWindowEP.EP_NAME.getExtensions()) {
      if (id.equals(ep.id)) {
        ToolWindowFactory factory = ep.getToolWindowFactory();
        return !factory.isDoNotActivateOnStart();
      }
    }
    return true;
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
  private void setTypeAndCheck(@NotNull ToolWindowType type) {
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

    if (myFloatingBounds != null) {
      ProjectFrameBoundsKt.serializeBounds(myFloatingBounds, element);
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

  @SuppressWarnings("HardCodedStringLiteral")
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

  boolean wasRead() {
    return myWasRead;
  }
}
