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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

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

  protected static final CacheEntry SEARCH_KEY = new CacheEntry(0, null, null, null);

  protected final Editor myEditor;
  protected final EditorTextRepresentationHelper myRepresentationHelper;
  protected final SoftWrapsStorage myStorage;

  private CacheEntry myCacheEntry;
  private T myEagerMatch;

  AbstractMappingStrategy(@NotNull Editor editor, @NotNull SoftWrapsStorage storage,
                          @NotNull EditorTextRepresentationHelper representationHelper) throws IllegalStateException
  {
    myEditor = editor;
    myStorage = storage;
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

  protected CacheEntry getCacheEntry() {
    return myCacheEntry;
  }

  protected void setCacheEntry(CacheEntry cacheEntry) {
    myCacheEntry = cacheEntry;
  }

  @NotNull
  @Override
  public EditorPosition buildInitialPosition() {
    return myCacheEntry.buildStartLineContext();
  }

  protected FoldingData getFoldRegionData(FoldRegion foldRegion) {
    return myCacheEntry.getFoldingData().get(foldRegion.getStartOffset());
  }

  @Override
  public T advance(EditorPosition position, int offset) {
    T result = buildIfExceeds(position, offset);
    if (result != null) {
      return result;
    }

    // Update context state and continue processing.
    position.logicalLine = myEditor.getDocument().getLineNumber(offset);
    int diff = offset - position.offset;
    position.visualColumn += diff;
    position.logicalColumn += diff;
    position.offset = offset;
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(EditorPosition context, int offset);

  @Override
  public T processFoldRegion(EditorPosition position, FoldRegion foldRegion) {
    T result = buildIfExceeds(position, foldRegion);
    if (result != null) {
      return result;
    }

    Document document = myEditor.getDocument();
    int endOffsetLogicalLine = document.getLineNumber(foldRegion.getEndOffset());
    int collapsedSymbolsWidthInColumns;
    if (position.logicalLine == endOffsetLogicalLine) {
      // Single-line fold region.
      FoldingData foldingData = getFoldRegionData(foldRegion);
      if (foldingData == null) {
        assert false;
        collapsedSymbolsWidthInColumns = position.visualColumn * myRepresentationHelper.textWidth(" ", 0, 1, Font.PLAIN, 0);
      }
      else {
        collapsedSymbolsWidthInColumns = foldingData.getCollapsedSymbolsWidthInColumns();
      }
    }
    else {
      // Multi-line fold region.
      collapsedSymbolsWidthInColumns = myRepresentationHelper.toVisualColumnSymbolsNumber(
        document.getCharsSequence(), foldRegion.getStartOffset(), foldRegion.getEndOffset(), 0
      );
      position.softWrapColumnDiff = 0;
      position.softWrapLinesBefore += position.softWrapLinesCurrent;
      position.softWrapLinesCurrent = 0;
    }

    position.advance(foldRegion, collapsedSymbolsWidthInColumns);
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(@NotNull EditorPosition context, @NotNull FoldRegion foldRegion);

  @Override
  public T processTabulation(EditorPosition position, TabData tabData) {
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
