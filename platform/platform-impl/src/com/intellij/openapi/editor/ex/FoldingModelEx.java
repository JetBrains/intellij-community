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

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author max
 */
public interface FoldingModelEx extends FoldingModel {
  void setFoldingEnabled(boolean isEnabled);
  boolean isFoldingEnabled();

  FoldRegion getFoldingPlaceholderAt(Point p);

  boolean intersectsRegion(int startOffset, int endOffset);

  FoldRegion fetchOutermost(int offset);

  int getLastCollapsedRegionBefore(int offset);

  TextAttributes getPlaceholderAttributes();

  FoldRegion[] fetchTopLevel();

  @Nullable
  FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, FoldingGroup group);
}
