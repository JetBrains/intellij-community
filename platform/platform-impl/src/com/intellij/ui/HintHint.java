/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.editor.Editor;

import java.awt.*;
import java.awt.event.MouseEvent;

public class HintHint {

  private Component myOriginalComponent;
  private Point myOriginalPoint;

  private boolean myStandardHint = false;

  public HintHint(MouseEvent e) {
    this(e.getComponent(), e.getPoint());
  }

  public HintHint(Editor editor, Point point) {
    this(editor.getContentComponent(), point);
  }

  public HintHint(Component originalComponent, Point originalPoint) {
    myOriginalComponent = originalComponent;
    myOriginalPoint = originalPoint;
  }

  public HintHint setTreatAsStandard(boolean isStandard) {
    myStandardHint = isStandard;
    return this;
  }

  public boolean isStandardHint() {
    return myStandardHint;
  }
}
