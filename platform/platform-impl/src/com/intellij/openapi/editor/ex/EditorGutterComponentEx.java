// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import gnu.trove.TIntFunction;
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

  @Nullable
  public GutterMark getGutterRenderer(final Point p) {
    return null;
  }
}
