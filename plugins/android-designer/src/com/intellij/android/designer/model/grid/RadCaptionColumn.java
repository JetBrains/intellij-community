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
package com.intellij.android.designer.model.grid;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class RadCaptionColumn<T extends RadViewComponent> extends RadCaptionComponent<T> {
  public RadCaptionColumn(EditableArea mainArea, T container, int index, int offset, int width, boolean empty) {
    super(mainArea, container, index, offset, width, empty);
  }

  @Override
  public Rectangle getBounds() {
    Rectangle bounds = myContainer.getBounds();
    return new Rectangle(bounds.x + myOffset, 2, myWidth, 10);
  }

  @Override
  public Rectangle getBounds(Component relativeTo) {
    Rectangle bounds = super.getBounds(relativeTo);
    bounds.y = 2;
    bounds.height = 10;
    return bounds;
  }
}