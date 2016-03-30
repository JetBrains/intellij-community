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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author max
 */
public interface FoldingModelEx extends FoldingModel {
  void setFoldingEnabled(boolean isEnabled);
  boolean isFoldingEnabled();

  FoldRegion getFoldingPlaceholderAt(Point p);

  boolean intersectsRegion(int startOffset, int endOffset);

  /**
   * Returns an index in an array returned by {@link #fetchTopLevel()} method, for the last folding region lying entirely before given
   * offset (region can touch given offset at its right edge).
   */
  int getLastCollapsedRegionBefore(int offset);

  TextAttributes getPlaceholderAttributes();

  FoldRegion[] fetchTopLevel();

  @Nullable
  FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, @Nullable FoldingGroup group,
                              boolean neverExpands);

  void addListener(@NotNull FoldingListener listener, @NotNull Disposable parentDisposable);

  void clearFoldRegions();

  void rebuild();
  
  @NotNull
  List<FoldRegion> getGroupedRegions(FoldingGroup group);
  
  void clearDocumentRangesModificationStatus();
  
  boolean hasDocumentRegionChangedFor(@NotNull FoldRegion region);
}
