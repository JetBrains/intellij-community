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
package com.intellij.android.designer.designSurface.layout.relative;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class ResizeSnapPoint extends SnapPoint {
  public ResizeSnapPoint(RadViewComponent component, boolean horizontal) {
    super(component, horizontal);
  }

  @Override
  public final boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    throw new UnsupportedOperationException();
  }

  public boolean processBounds(List<RadComponent> components, Rectangle bounds, Side resizeSide, SnapPointFeedbackHost feedback) {
    return super.processBounds(components, bounds, feedback);
  }
}