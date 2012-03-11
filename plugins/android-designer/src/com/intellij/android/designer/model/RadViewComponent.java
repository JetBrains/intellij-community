/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.psi.xml.XmlTag;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadViewComponent extends RadComponent {
  private final List<RadComponent> myChildren = new ArrayList<RadComponent>();
  private ViewInfo myViewInfo;
  private Component myNativeComponent;
  private final Rectangle myBounds = new Rectangle();
  private XmlTag myTag;
  private List<Property> myProperties;

  public XmlTag getTag() {
    return myTag;
  }

  public void setTag(XmlTag tag) {
    myTag = tag;
  }

  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  public ViewInfo getViewInfo() {
    return myViewInfo;
  }

  public void setViewInfo(ViewInfo viewInfo) {
    myViewInfo = viewInfo;
  }

  @Override
  public Rectangle getBounds() {
    return myBounds;
  }

  @Override
  public Rectangle getBounds(Component relativeTo) {
    return SwingUtilities.convertRectangle(myNativeComponent, myBounds, relativeTo);
  }

  public void setBounds(int x, int y, int width, int height) {
    myBounds.setBounds(x, y, width, height);
  }

  public Component getNativeComponent() {
    return myNativeComponent;
  }

  public void setNativeComponent(Component nativeComponent) {
    myNativeComponent = nativeComponent;
  }

  @Override
  public Point convertPoint(Component component, int x, int y) {
    return SwingUtilities.convertPoint(component, x, y, myNativeComponent);
  }

  @Override
  public List<Property> getProperties() {
    return myProperties;
  }

  public void setProperties(List<Property> properties) {
    myProperties = properties;
  }
}