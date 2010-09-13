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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
* @author Denis Zhdanov
* @since Sep 9, 2010 9:20:44 AM
*/
class VisualToLogicalCalculationStrategy extends AbstractMappingStrategy<LogicalPosition> {

  private final VisualPosition myTargetVisual;

  VisualToLogicalCalculationStrategy(@NotNull final VisualPosition targetVisual, final List<CacheEntry> cache, Editor editor,
                                     SoftWrapsStorage storage, EditorTextRepresentationHelper representationHelper) {
    super(new Computable<Pair<CacheEntry, LogicalPosition>>() {
      @Override
      public Pair<CacheEntry, LogicalPosition> compute() {
        SEARCH_KEY.visualLine = targetVisual.line;
        int i = Collections.binarySearch(cache, SEARCH_KEY);
        if (i >= 0) {
          CacheEntry cacheEntry = cache.get(i);
          LogicalPosition eager = null;
          if (targetVisual.column == 0) {
            eager = cacheEntry.buildStartLineContext().buildLogicalPosition();
          }
          return new Pair<CacheEntry, LogicalPosition>(cacheEntry, eager);
        }

        // Handle situation with corrupted cache.
        throw new IllegalStateException(String.format(
          "Can't map visual position (%s) to logical. Reason: no cached information information about target visual line is found. "
          + "Registered entries: %s", targetVisual, cache
        ));
      }
    }, editor, storage, representationHelper);
    myTargetVisual = targetVisual;
  }

  @Nullable
  @Override
  public LogicalPosition eagerMatch() {
    return null;
  }

  @Override
  protected LogicalPosition buildIfExceeds(ProcessingContext context, int offset) {
    // There is a possible case that target visual line starts with soft wrap. We want to process that at 'processSoftWrap()' method.
    if (offset == myCacheEntry.startOffset && myStorage.getSoftWrap(offset) != null) {
      return null;
    }

    // Return eagerly if target visual position remains between current context position and the one defined by the given offset.
    if (offset > myCacheEntry.endOffset || (context.visualColumn + offset - context.offset >= myTargetVisual.column)) {
      int diff = myTargetVisual.column - context.visualColumn;
      context.offset = Math.min(myCacheEntry.endOffset, context.offset + diff);
      context.logicalColumn += diff;
      context.visualColumn = myTargetVisual.column;
      return context.buildLogicalPosition();
    }
    return null;
  }

  @Nullable
  @Override
  public LogicalPosition processSoftWrap(ProcessingContext context, SoftWrap softWrap) {
    // There is a possible case that current visual line starts with soft wrap and target visual position points to its
    // virtual space.
    if (myCacheEntry.startOffset == softWrap.getStart()) {
      if (myTargetVisual.column <= softWrap.getIndentInColumns()) {
        ProcessingContext resultingContext = myCacheEntry.buildStartLineContext();
        resultingContext.visualColumn = myTargetVisual.column;
        resultingContext.softWrapColumnDiff += myTargetVisual.column;
        return resultingContext.buildLogicalPosition();
      }
      else {
        context.visualColumn = softWrap.getIndentInColumns();
        context.softWrapColumnDiff += softWrap.getIndentInColumns();
        return null;
      }
    }

    // We assume that target visual position points to soft wrap-introduced virtual space if this method is called (we expect
    // to iterate only a single visual line and also expect soft wrap to have line feed symbol at the first position).
    ProcessingContext targetContext = myCacheEntry.buildEndLineContext();
    targetContext.softWrapColumnDiff += myTargetVisual.column - targetContext.visualColumn;
    targetContext.visualColumn = myTargetVisual.column;
    return targetContext.buildLogicalPosition();
  }

  @Override
  protected LogicalPosition buildIfExceeds(@NotNull ProcessingContext context, @NotNull FoldRegion foldRegion) {
    // We assume that fold region placeholder contains only 'simple' symbols, i.e. symbols that occupy single visual column.
    String placeholder = foldRegion.getPlaceholderText();

    // Check if target visual position points inside collapsed fold region placeholder
    if (myTargetVisual.column < /* note that we don't use <= here */ context.visualColumn + placeholder.length()) {
      // Map all visual positions that point inside collapsed fold region as the logical position of it's start.
      return context.buildLogicalPosition();
    }

    return null;
  }

  @Override
  protected LogicalPosition buildIfExceeds(ProcessingContext context, TabData tabData) {
    if (context.visualColumn + tabData.widthInColumns >= myTargetVisual.column) {
      context.logicalColumn += myTargetVisual.column - context.visualColumn;
      context.visualColumn = myTargetVisual.column;
      return context.buildLogicalPosition();
    }
    return null;
  }

  @NotNull
  @Override
  public LogicalPosition build(ProcessingContext context) {
    int diff = myTargetVisual.column - context.visualColumn;
    context.logicalColumn += diff;
    context.offset += diff;
    context.visualColumn = myTargetVisual.column;
    return context.buildLogicalPosition();
  }
}
