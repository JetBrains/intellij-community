/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.notification.impl.ui;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;

/**
 * @author spleaner
 */
public class StickyButton extends JToggleButton {

  public StickyButton(final String text) {
    super(text);

    setRolloverEnabled(true);
    setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
    setUI(new StickyButtonUI());
  }

  @Override
  public void setUI(ButtonUI ui) {
    if (ui instanceof StickyButtonUI) {
      super.setUI(ui);
    } else {
      super.setUI(createUI());
    }
  }

  protected ButtonUI createUI() {
    return new StickyButtonUI();
  }

  @Override
  public Color getForeground() {
    if (/*model.isRollover() || */ model.isSelected()) {
      return Color.WHITE;
    }

    return super.getForeground();
  }
}
