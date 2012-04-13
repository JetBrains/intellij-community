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
package com.intellij.android.designer.model.table;

import com.intellij.android.designer.designSurface.layout.CaptionStaticDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadCaptionTableRow extends RadVisualComponent {
  private final RadViewComponent myComponent;
  private final StaticDecorator myDecorator = new CaptionStaticDecorator(this);

  public RadCaptionTableRow(RadTableLayoutComponent container, RadViewComponent component, EditableArea mainArea) {
    myComponent = component;
    setNativeComponent(container.getNativeComponent());
  }

  @Override
  public Rectangle getBounds() {
    Rectangle bounds = myComponent.getBounds();
    return new Rectangle(3, bounds.y, 10, bounds.height);
  }

  @Override
  public Rectangle getBounds(Component relativeTo) {
    Rectangle bounds = super.getBounds(relativeTo);
    bounds.x = 3;
    bounds.width = 10;
    return bounds;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    decorators.add(myDecorator);
  }
}