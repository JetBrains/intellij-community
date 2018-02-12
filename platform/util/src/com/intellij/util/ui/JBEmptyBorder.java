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
package com.intellij.util.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBEmptyBorder extends EmptyBorder {
  public JBEmptyBorder(int top, int left, int bottom, int right) {
    super(JBUI.insets(top, left, bottom, right));
  }

  public JBEmptyBorder(Insets insets) {
    super(JBUI.insets(insets));
  }

  public JBEmptyBorder(int offset) {
    this(offset, offset, offset, offset);
  }

  public JBEmptyBorderUIResource asUIResource() {
    return new JBEmptyBorderUIResource(this);
  }

  public <T extends JComponent> T wrap(T component) {
    component.setBorder(this);
    return component;
  }

  public static class JBEmptyBorderUIResource extends JBEmptyBorder implements UIResource {
    public JBEmptyBorderUIResource(JBEmptyBorder border) {
      super(0,0,0,0);
      top = border.top;
      left = border.left;
      bottom = border.bottom;
      right = border.right;
    }
  }
}
