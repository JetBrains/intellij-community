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

import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class BorderLayoutPanel extends JBPanel<BorderLayoutPanel> {
  public BorderLayoutPanel() {
    this(0, 0);
  }

  public BorderLayoutPanel(int hgap, int vgap) {
    super(new BorderLayout(JBUI.scale(hgap), JBUI.scale(vgap)));
  }

  @NotNull
  public BorderLayoutPanel addToCenter(@NotNull Component comp) {
    add(comp, BorderLayout.CENTER);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToRight(@NotNull Component comp) {
    add(comp, BorderLayout.EAST);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToLeft(@NotNull Component comp) {
    add(comp, BorderLayout.WEST);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToTop(@NotNull Component comp) {
    add(comp, BorderLayout.NORTH);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToBottom(@NotNull Component comp) {
    add(comp, BorderLayout.SOUTH);
    return this;
  }
}
