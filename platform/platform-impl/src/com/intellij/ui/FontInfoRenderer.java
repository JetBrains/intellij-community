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
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.FontInfo;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public class FontInfoRenderer extends ListCellRendererWrapper {
  @Override
  public void customize(JList list, Object value, int index, boolean selected, boolean focused) {
    Font font = list.getFont();
    String text = value == null ? "" : value.toString();
    setText(text);
    if (value instanceof FontInfo) {
      FontInfo info = (FontInfo)value;
      Integer size = getFontSize();
      Font f = info.getFont(size != null ? size : font.getSize());
      if (f.canDisplayUpTo(text) == -1) {
        font = f;
      }
    }
    setFont(font);
    setForeground(list.isEnabled()
                  ? UIUtil.getListForeground(selected)
                  : UIUtil.getLabelDisabledForeground());

    AntialiasingType type = getAntialiasingType();
    if (type == null) type = AntialiasingType.GREYSCALE;
    setClientProperty(SwingUtilities2.AA_TEXT_PROPERTY_KEY, type.getTextInfo());
  }

  protected Integer getFontSize() {
    return null;
  }

  protected AntialiasingType getAntialiasingType() {
    return UISettings.getShadowInstance().IDE_AA_TYPE;
  }
}
