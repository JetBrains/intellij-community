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
package com.intellij.openapi.editor.ex.util;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Denis Fokin
 */
public class EditorUIUtil {

  /* This method has to be used for setting up antialiasing and rendering hints in
 * editors only.
 */
  public static void setupAntialiasing(final Graphics g) {

    Graphics2D g2d = (Graphics2D)g;

    int lcdContrastValue = UIUtil.getLcdContrastValue();

    g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, lcdContrastValue);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(true));

    UISettings.setupFractionalMetrics(g2d);
  }
}
