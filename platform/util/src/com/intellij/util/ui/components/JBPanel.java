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
package com.intellij.util.ui.components;

import com.intellij.util.ui.JBFont;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("unchecked")
public class JBPanel<T extends JBPanel> extends JPanel implements JBComponent<T> {

  public JBPanel(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
  }

  public JBPanel(LayoutManager layout) {
    super(layout);
  }

  public JBPanel(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  public JBPanel() {
    super();
  }

  @Override
  public T withBorder(Border border) {
    setBorder(border);
    return (T)this;
  }

  @Override
  public T withFont(JBFont font) {
    setFont(font);
    return (T)this;
  }

  @Override
  public T andTransparent() {
    setOpaque(false);
    return (T)this;
  }

  @Override
  public T andOpaque() {
    setOpaque(true);
    return (T)this;
  }
}
