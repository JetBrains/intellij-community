/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.impl.VisualSizeChangeListener;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps soft-wraps specific document parsing to the {@link VisualSizeChangeListener} API.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/6/10 12:02 PM
 */
public class SoftWrapAwareVisualSizeManager extends SoftWrapAwareDocumentParsingListenerAdapter {

  private final List<VisualSizeChangeListener> myListeners  = new ArrayList<>();
  private final TIntIntHashMap                 myLineWidths = new TIntIntHashMap();

  private SoftWrapPainter myPainter;

  private int myLastLogicalLine;

  public SoftWrapAwareVisualSizeManager(@NotNull SoftWrapPainter painter) {
    myPainter = painter;
  }

  public boolean addVisualSizeChangeListener(@NotNull VisualSizeChangeListener listener) {
    return myListeners.add(listener);
  }

  @Override
  public void onCacheUpdateStart(@NotNull IncrementalCacheUpdateEvent event) {
    myLineWidths.clear();
  }

  @Override
  public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
    if (myListeners.isEmpty()) {
      return;
    }
    
    int startLine = event.getStartLogicalLine();
    int oldEndLine = event.getOldEndLogicalLine();
    for (VisualSizeChangeListener listener : myListeners) {
      listener.onLineWidthsChange(startLine, oldEndLine, myLastLogicalLine, myLineWidths);
    }
  }

  @Override
  public void onVisualLineEnd(@NotNull EditorPosition position) {
    if (myListeners.isEmpty()) {
      return;
    }
    updateLineWidthIfNecessary(position.logicalLine, position.x);
    myLastLogicalLine = position.logicalLine;
  }

  @Override
  public void beforeSoftWrapLineFeed(@NotNull EditorPosition position) {
    int newWidth = position.x + myPainter.getMinDrawingWidth(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    myLastLogicalLine = position.logicalLine;
    if (!myLineWidths.contains(position.logicalLine)) {
      myLineWidths.put(position.logicalLine, newWidth);
      return;
    }
    
    updateLineWidthIfNecessary(position.logicalLine, newWidth);
  }

  private void updateLineWidthIfNecessary(int line, int width) {
    int storedWidth = myLineWidths.get(line);
    if (width > storedWidth) {
      myLineWidths.put(line, width);
    }
  }

  @TestOnly
  public void setSoftWrapPainter(SoftWrapPainter painter) {
    myPainter = painter;
  }
}
