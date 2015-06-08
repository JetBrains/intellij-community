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

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface Splittable {
  float getMinProportion(boolean first);

  void setProportion(float proportion);

  /**
   * @return <code>true</code> if splitter has vertical orientation, <code>false</code> otherwise
   */
  boolean getOrientation();

  /**
   * @param verticalSplit <code>true</code> means that splitter will have vertical split
   */
  void setOrientation(boolean verticalSplit);

  @NotNull
  Component asComponent();

  void setDragging(boolean dragging);
}
