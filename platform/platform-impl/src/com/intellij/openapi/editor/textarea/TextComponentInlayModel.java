// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class TextComponentInlayModel implements InlayModel {
  @Nullable
  @Override
  public Inlay addInlineElement(int offset, boolean relatesToPrecedingText, @NotNull EditorCustomElementRenderer renderer) {
    return null;
  }

  @NotNull
  @Override
  public List<Inlay> getInlineElementsInRange(int startOffset, int endOffset) {
    return Collections.emptyList();
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    return false;
  }

  @Nullable
  @Override
  public Inlay getInlineElementAt(@NotNull VisualPosition visualPosition) {
    return null;
  }

  @Nullable
  @Override
  public Inlay getElementAt(@NotNull Point point) {
    return null;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
  }
}
