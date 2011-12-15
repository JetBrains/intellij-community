/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abstract super class for mapping strategies that encapsulates shared logic of advancing the context to the points
 * of specific types. I.e. it's main idea is to ask sub-class if target dimension lays inside particular region
 * (e.g. soft wrap, fold region etc) and use common algorithm for advancing context to the region's end in the case
 * of negative answer.
 * <p/>
 * Not thread-safe.
 *
 * @param <T>     resulting document dimension type
 */
abstract class AbstractMappingStrategy<T> implements MappingStrategy<T> {
  
  private static final Logger LOG = Logger.getInstance("#" + AbstractMappingStrategy.class.getName());

  protected final Editor myEditor;
  protected final EditorTextRepresentationHelper myRepresentationHelper;
  protected final SoftWrapsStorage myStorage;
  protected final List<CacheEntry> myCache;

  private EditorPosition myInitialPosition;
  private CacheEntry myTargetEntry;
  private T myEagerMatch;
  private int myLastEntryOffset;

  AbstractMappingStrategy(@NotNull Editor editor,
                          @NotNull SoftWrapsStorage storage,
                          @NotNull List<CacheEntry> cache,
                          @NotNull EditorTextRepresentationHelper representationHelper)
  {
    myEditor = editor;
    myStorage = storage;
    myCache = cache;
    myRepresentationHelper = representationHelper;
  }

  @Nullable
  @Override
  public T eagerMatch() {
    return myEagerMatch;
  }

  protected void setEagerMatch(@Nullable T eagerMatch) {
    myEagerMatch = eagerMatch;
  }

  protected void setFirstInitialPosition() {
    myInitialPosition = new EditorPosition(new LogicalPosition(0, 0), 0, myEditor, myRepresentationHelper);
  }

  @Nullable
  protected CacheEntry getTargetEntry() {
    return myTargetEntry;
  }

  protected void setTargetEntry(@NotNull CacheEntry targetEntry, boolean anchorToStart) {
    myTargetEntry = targetEntry;
    if (anchorToStart) {
      myInitialPosition = targetEntry.buildStartLinePosition();
    }
    else {
      myInitialPosition = targetEntry.buildEndLinePosition();
    }
  }

  protected void reset() {
    myEagerMatch = null;
    myTargetEntry = null;
    myInitialPosition = null;
    if (!myCache.isEmpty()) {
      myLastEntryOffset = myCache.get(myCache.size() - 1).endOffset;
    }
  }
  
  protected void setInitialPosition(@NotNull EditorPosition position) {
    myInitialPosition = position;
  }

  @NotNull
  @Override
  public EditorPosition buildInitialPosition() {
    return myInitialPosition;
  }

  @Nullable
  protected FoldingData getFoldRegionData(@NotNull FoldRegion foldRegion) {
    int i = MappingUtil.getCacheEntryIndexForOffset(foldRegion.getStartOffset(), myEditor.getDocument(), myCache);
    if (i < 0 || i >= myCache.size()) {
      return null;
    }

    CacheEntry cacheEntry = myCache.get(i);
    return cacheEntry.getFoldingData(foldRegion);
  }

  @Override
  public T advance(@NotNull EditorPosition position, int offset) {
    Document document = myEditor.getDocument();
    if (offset >= myLastEntryOffset || offset >= document.getTextLength()) {
      return build(position);
    }
    
    T result = buildIfExceeds(position, offset);
    if (result != null) {
      return result;
    }

    // Update context state and continue processing.
    int linesDiff = document.getLineNumber(offset) - position.logicalLine;
    position.logicalLine += linesDiff;
    position.visualLine += linesDiff;
    if (linesDiff <= 0) {
      int columnsDiff = offset - position.offset;
      position.visualColumn += columnsDiff;
      position.logicalColumn += columnsDiff;
    }
    else {
      int lineStartOffset = document.getLineStartOffset(position.logicalLine);
      int column = offset - lineStartOffset;
      position.visualColumn = column;
      position.logicalColumn = column;
    }
    position.offset = offset;
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(EditorPosition position, int offset);

  @Override
  public T processFoldRegion(@NotNull EditorPosition position, @NotNull FoldRegion foldRegion) {
    T result = buildIfExceeds(position, foldRegion);
    if (result != null) {
      return result;
    }

    Document document = myEditor.getDocument();
    int endOffsetLogicalLine = document.getLineNumber(foldRegion.getEndOffset());
    int collapsedSymbolsWidthInColumns = -1;
    if (position.logicalLine == endOffsetLogicalLine) {
      // Single-line fold region.
      FoldingData foldingData = getFoldRegionData(foldRegion);
      if (foldingData != null) {
        collapsedSymbolsWidthInColumns = foldingData.getCollapsedSymbolsWidthInColumns();
      }
      else {
        String details = "";
        if (myEditor instanceof EditorImpl) {
          details = ((EditorImpl)myEditor).dumpState();
        }
        LogMessageEx.error(LOG, "Unexpected fold region is found: " + foldRegion, details);
      }
    }
    else {
      // Multi-line fold region.
      position.softWrapColumnDiff = 0;
      position.softWrapLinesBefore += position.softWrapLinesCurrent;
      position.softWrapLinesCurrent = 0;
    }
    
    if (collapsedSymbolsWidthInColumns < 0) {
      collapsedSymbolsWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(
        document.getCharsSequence(), foldRegion.getStartOffset(), foldRegion.getEndOffset(), 0
      );
    }
    
    position.advance(foldRegion, collapsedSymbolsWidthInColumns);
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(@NotNull EditorPosition context, @NotNull FoldRegion foldRegion);

  @Override
  public T processTabulation(@NotNull EditorPosition position, TabData tabData) {
    T result = buildIfExceeds(position, tabData);
    if (result != null) {
      return result;
    }

    position.visualColumn += tabData.widthInColumns;
    position.logicalColumn += tabData.widthInColumns;
    position.offset++;
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(EditorPosition context, TabData tabData);
}
