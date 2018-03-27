/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class EditorGutterComponentEx extends JComponent implements EditorGutter {
  @Nullable
  public abstract FoldRegion findFoldingAnchorAt(int x, int y);

  public abstract int getWhitespaceSeparatorOffset();

  public abstract void revalidateMarkup();

  public abstract int getLineMarkerAreaOffset();
  
  public abstract int getIconAreaOffset();
  
  public abstract int getLineMarkerFreePaintersAreaOffset();

  public abstract int getIconsAreaWidth();

  public abstract int getAnnotationsAreaOffset();

  public abstract int getAnnotationsAreaWidth();

  @Nullable
  public abstract Point getCenterPoint(GutterIconRenderer renderer);

  public abstract void setLineNumberConvertor(@Nullable TIntFunction lineNumberConvertor);

  public abstract void setLineNumberConvertor(@Nullable TIntFunction lineNumberConvertor1, @Nullable TIntFunction lineNumberConvertor2);

  public abstract void setShowDefaultGutterPopup(boolean show);

  public abstract void setGutterPopupGroup(@Nullable ActionGroup group);
  
  public abstract void setPaintBackground(boolean value);

  public abstract void setForceShowLeftFreePaintersArea(boolean value);

  public abstract void setForceShowRightFreePaintersArea(boolean value);

  public abstract void setInitialIconAreaWidth(int width);
}
