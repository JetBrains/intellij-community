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

import com.intellij.designer.model.RadComponent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO: now dummy implementation for tests
 *
 * @author Alexander Lobas
 */
public class RadViewComponent extends RadComponent {
  private final String myTitle;
  private final List<RadComponent> myChildren = new ArrayList<RadComponent>();
  private Component myNativeComponent;
  private final Rectangle myBounds = new Rectangle();

  public RadViewComponent(RadViewComponent parent, String title) {
    myTitle = title;
    setParent(parent);
    if (parent != null) {
      parent.getChildren().add(this);
    }
    setLayout(new RadViewLayout());
  }

  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  public String getTitle() {
    return myTitle;
  }

  @Override
  public Rectangle getBounds() {
    return myBounds;
  }

  public void setBounds(int x, int y, int width, int height) {
    myBounds.setBounds(x, y, width, height);
  }

  public void setNativeComponent(Component nativeComponent) {
    myNativeComponent = nativeComponent;
  }

  @Override
  public Point convertPoint(Component component, int x, int y) {
    return SwingUtilities.convertPoint(component, x, y, myNativeComponent);
  }

  @Override
  public Point convertPoint(int x, int y, Component component) {
    return SwingUtilities.convertPoint(myNativeComponent, x, y, component);
  }
}