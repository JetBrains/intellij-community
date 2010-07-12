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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.FoldRegion;

import javax.swing.*;
import java.awt.*;

public abstract class EditorGutterComponentEx extends JComponent implements EditorGutter {

  public abstract boolean isFoldingOutlineShown();

  public abstract boolean isLineMarkersShown();

  public abstract FoldRegion findFoldingAnchorAt(int x, int y);

  public abstract int getWhitespaceSeparatorOffset();

  public abstract Color getOutlineColor(boolean isActive);

  /**
   * @deprecated use getOutlineColor
   */
  public Color getFoldingColor(boolean isActive) {
    return getOutlineColor(isActive);
  }

  public abstract void revalidateMarkup();

  public abstract int getLineMarkerAreaOffset();

  public abstract int getIconsAreaWidth();
}
