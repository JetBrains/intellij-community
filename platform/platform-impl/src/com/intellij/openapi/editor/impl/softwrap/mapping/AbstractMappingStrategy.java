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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
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

  protected final CacheEntry myCacheEntry;
  protected final Editor myEditor;
  protected final EditorTextRepresentationHelper myRepresentationHelper;
  protected final SoftWrapsStorage myStorage;
  private final T myEagerMatch;

  AbstractMappingStrategy(Computable<Pair<CacheEntry, T>> cacheEntryProvider, Editor editor, SoftWrapsStorage storage,
                          EditorTextRepresentationHelper representationHelper) throws IllegalStateException
  {
    myEditor = editor;
    myStorage = storage;
    myRepresentationHelper = representationHelper;

    Pair<CacheEntry, T> pair = cacheEntryProvider.compute();
    myCacheEntry = pair.first;
    myEagerMatch = pair.second;
  }

  @Nullable
  @Override
  public T eagerMatch() {
    return myEagerMatch;
  }

  @NotNull
  @Override
  public ProcessingContext buildInitialContext() {
    return myCacheEntry.buildStartLineContext();
  }

  protected FoldingData getFoldRegionData(FoldRegion foldRegion) {
    return myCacheEntry.getFoldingData().get(foldRegion.getStartOffset());
  }

  @Override
  public T advance(ProcessingContext context, int offset) {
    T result = buildIfExceeds(context, offset);
    if (result != null) {
      return result;
    }

    // Update context state and continue processing.
    context.logicalLine = myEditor.getDocument().getLineNumber(offset);
    int diff = offset - context.offset;
    context.visualColumn += diff;
    context.logicalColumn += diff;
    context.offset = offset;
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(ProcessingContext context, int offset);

  @Override
  public T processFoldRegion(ProcessingContext context, FoldRegion foldRegion) {
    T result = buildIfExceeds(context, foldRegion);
    if (result != null) {
      return result;
    }

    Document document = myEditor.getDocument();
    int endOffsetLogicalLine = document.getLineNumber(foldRegion.getEndOffset());
    int collapsedSymbolsWidthInColumns;
    if (context.logicalLine == endOffsetLogicalLine) {
      // Single-line fold region.
      FoldingData foldingData = getFoldRegionData(foldRegion);
      if (foldingData == null) {
        assert false;
        collapsedSymbolsWidthInColumns = context.visualColumn * myRepresentationHelper.textWidth(" ", 0, 1, Font.PLAIN, 0);
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
      context.softWrapColumnDiff = 0;
      context.softWrapLinesBefore += context.softWrapLinesCurrent;
      context.softWrapLinesCurrent = 0;
    }

    context.advance(foldRegion, collapsedSymbolsWidthInColumns);
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(ProcessingContext context, FoldRegion foldRegion);

  @Override
  public T processTabulation(ProcessingContext context, TabData tabData) {
    T result = buildIfExceeds(context, tabData);
    if (result != null) {
      return result;
    }

    context.visualColumn += tabData.widthInColumns;
    context.logicalColumn += tabData.widthInColumns;
    context.offset++;
    return null;
  }

  @Nullable
  protected abstract T buildIfExceeds(ProcessingContext context, TabData tabData);
}
