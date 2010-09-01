/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.SoftWrapModel;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since Jun 22, 2010 5:56:23 PM
 */
public class TextComponentSoftWrapModel implements SoftWrapModel {

  @Override
  public boolean isSoftWrappingEnabled() {
    return false;
  }

  @Nullable
  @Override
  public TextChange getSoftWrap(int offset) {
    return null;
  }

  @NotNull
  @Override
  public List<? extends TextChange> getSoftWrapsForLine(int documentLine) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<? extends TextChange> getSoftWrapsForRange(int start, int end) {
    return Collections.emptyList();
  }

  @Override
  public boolean isVisible(TextChange softWrap) {
    return false;
  }

  @Override
  public void beforeDocumentChangeAtCaret() {
  }

  @Override
  public boolean isInsideSoftWrap(@NotNull VisualPosition position) {
    return false;
  }

  @Override
  public boolean isInsideOrBeforeSoftWrap(@NotNull VisualPosition visual) {
    return false;
  }

  @Override
  public int getSoftWrapIndentWidthInPixels(@NotNull TextChange softWrap) {
    return 0;
  }

  @Override
  public int getSoftWrapIndentWidthInColumns(@NotNull TextChange softWrap) {
    return 0;
  }
}
