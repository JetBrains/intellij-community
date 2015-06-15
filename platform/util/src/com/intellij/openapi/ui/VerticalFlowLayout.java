/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;

import java.awt.*;
import java.io.Serializable;

public class VerticalFlowLayout extends FlowLayout implements Serializable {
  public static final int BOTTOM = 2;
  public static final int MIDDLE = 1;
  public static final int TOP = 0;
  private boolean myVerticalFill;
  private boolean myHorizontalFill;
  private final int vGap;
  private final int hGap;

  @MagicConstant(intValues = {TOP, MIDDLE, BOTTOM})
  public @interface VerticalFlowAlignment {}

  public VerticalFlowLayout() {
    this(TOP, 5, 5, true, false);
  }

  public VerticalFlowLayout(@VerticalFlowAlignment int alignment) {
    this(alignment, 5, 5, true, false);
  }

  public VerticalFlowLayout(boolean fillHorizontally, boolean fillVertically) {
    this(TOP, 5, 5, fillHorizontally, fillVertically);
  }

  public VerticalFlowLayout(@VerticalFlowAlignment int alignment, boolean fillHorizontally, boolean fillVertically) {
    this(alignment, 5, 5, fillHorizontally, fillVertically);
  }

  public VerticalFlowLayout(int hGap, int vGap) {
    this(TOP, hGap, vGap, true, false);
  }

  public VerticalFlowLayout(@VerticalFlowAlignment int alignment, int hGap, int vGap, boolean fillHorizontally, boolean fillVertically) {
    setAlignment(alignment);
    this.hGap = hGap;
    this.vGap = vGap;
    myHorizontalFill = fillHorizontally;
    myVerticalFill = fillVertically;
  }

  @Override
  public void layoutContainer(Container container) {
    Insets insets = container.getInsets();
    int i = container.getSize().height - (insets.top + insets.bottom + vGap * 2);
    int j = container.getSize().width - (insets.left + insets.right + hGap * 2);
    int k = container.getComponentCount();
    int l = insets.left + hGap;
    int i1 = 0;
    int j1 = 0;
    int k1 = 0;
    for(int l1 = 0; l1 < k; l1++){
      Component component = container.getComponent(l1);
      if (!component.isVisible()) continue;
      Dimension dimension = component.getPreferredSize();
      if (myVerticalFill && l1 == k - 1){
        dimension.height = Math.max(i - i1, component.getPreferredSize().height);
      }
      if (myHorizontalFill){
        component.setSize(j, dimension.height);
        dimension.width = j;
      }
      else{
        component.setSize(dimension.width, dimension.height);
      }
      if (i1 + dimension.height > i){
        a(container, l, insets.top + vGap, j1, i - i1, k1, l1);
        i1 = dimension.height;
        l += hGap + j1;
        j1 = dimension.width;
        k1 = l1;
        continue;
      }
      if (i1 > 0){
        i1 += vGap;
      }
      i1 += dimension.height;
      j1 = Math.max(j1, dimension.width);
    }

    a(container, l, insets.top + vGap, j1, i - i1, k1, k);
  }

  private void a(Container container, int i, int j, int k, int l, int i1, int j1) {
    int k1 = getAlignment();
    if (k1 == 1){
      j += l / 2;
    }
    if (k1 == 2){
      j += l;
    }
    for(int l1 = i1; l1 < j1; l1++){
      Component component = container.getComponent(l1);
      Dimension dimension = component.getSize();
      if (component.isVisible()){
        int i2 = i + (k - dimension.width) / 2;
        component.setLocation(i2, j);
        j += vGap + dimension.height;
      }
    }
  }

  public boolean getHorizontalFill() {
    return myHorizontalFill;
  }

  public void setHorizontalFill(boolean flag) {
    myHorizontalFill = flag;
  }

  public boolean getVerticalFill() {
    return myVerticalFill;
  }

  public void setVerticalFill(boolean flag) {
    myVerticalFill = flag;
  }

  @Override
  public Dimension minimumLayoutSize(Container container) {
    Dimension dimension = JBUI.emptySize();
    for(int i = 0; i < container.getComponentCount(); i++){
      Component component = container.getComponent(i);
      if (!component.isVisible()) continue;
      Dimension dimension1 = component.getMinimumSize();
      dimension.width = Math.max(dimension.width, dimension1.width);
      if (i > 0){
        dimension.height += vGap;
      }
      dimension.height += dimension1.height;
    }
    addInsets(dimension, container);
    return dimension;
  }

  @Override
  public Dimension preferredLayoutSize(Container container) {
    Dimension dimension = JBUI.emptySize();
    for(int i = 0; i < container.getComponentCount(); i++){
      Component component = container.getComponent(i);
      if (!component.isVisible()) continue;
      Dimension dimension1 = component.getPreferredSize();
      dimension.width = Math.max(dimension.width, dimension1.width);
      if (i > 0){
        dimension.height += vGap;
      }
      dimension.height += dimension1.height;
    }
    addInsets(dimension, container);
    return dimension;
  }

  private void addInsets(Dimension dimension, Container container) {
    JBInsets.addTo(dimension, container.getInsets());
    dimension.width += hGap + hGap;
    dimension.height += vGap + vGap;
  }
}
