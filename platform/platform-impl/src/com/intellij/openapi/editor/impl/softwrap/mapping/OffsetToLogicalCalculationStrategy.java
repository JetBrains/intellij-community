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

import java.util.List;

/**
* @author Denis Zhdanov
* @since Sep 9, 2010 9:20:55 AM
*/
class OffsetToLogicalCalculationStrategy extends AbstractMappingStrategy<LogicalPosition> {

  private final int myTargetOffset;

  OffsetToLogicalCalculationStrategy(final int targetOffset, final Editor editor, EditorTextRepresentationHelper representationHelper,
                                     final List<CacheEntry> cache, SoftWrapsStorage storage)
  {
    super(new Computable<Pair<CacheEntry, LogicalPosition>>() {
      @Override
      public Pair<CacheEntry, LogicalPosition> compute() {
        if (targetOffset >= editor.getDocument().getTextLength()) {
          if (cache.isEmpty()) {
            return new Pair<CacheEntry, LogicalPosition>(null, new LogicalPosition(0, 0, 0, 0, 0, 0, 0));
          }
          else {
            CacheEntry lastEntry = cache.get(cache.size() - 1);
            LogicalPosition eager = new LogicalPosition(
              lastEntry.endLogicalLine, lastEntry.endLogicalColumn + 1, lastEntry.endSoftWrapLinesBefore,
              lastEntry.endSoftWrapLinesCurrent, lastEntry.endSoftWrapColumnDiff, lastEntry.endFoldedLines,
              lastEntry.endFoldingColumnDiff
            );
            return new Pair<CacheEntry, LogicalPosition>(null, eager);
          }
        }

        int i = MappingUtil.getCacheEntryIndexForOffset(targetOffset, editor.getDocument(), cache);
        return new Pair<CacheEntry, LogicalPosition>(cache.get(i), null);
      }
    }, editor, storage, representationHelper);
    myTargetOffset = targetOffset;
  }

  @Override
  protected LogicalPosition buildIfExceeds(ProcessingContext context, int offset) {
    if (myTargetOffset > offset) {
      return null;
    }

    // Process use-case when target offset points to 'after soft wrap' position.
    SoftWrap softWrap = myStorage.getSoftWrap(offset);
    if (softWrap != null && offset < myCacheEntry.endOffset) {
      context.visualColumn = softWrap.getIndentInColumns();
      context.softWrapColumnDiff = context.visualColumn - context.logicalColumn;
      return context.buildLogicalPosition();
    }

    int diff = myTargetOffset - context.offset;
    context.logicalColumn += diff;
    context.visualColumn += diff;
    context.offset = myTargetOffset;
    return context.buildLogicalPosition();
  }

  @Override
  protected LogicalPosition buildIfExceeds(@NotNull ProcessingContext context, @NotNull FoldRegion foldRegion) {
    if (myTargetOffset >= foldRegion.getEndOffset()) {
      return null;
    }

    Document document = myEditor.getDocument();
    int targetLogicalLine = document.getLineNumber(myTargetOffset);
    if (targetLogicalLine == context.logicalLine) {
      // Target offset is located on the same logical line as folding start.
      FoldingData cachedData = getFoldRegionData(foldRegion);
      context.logicalColumn += myRepresentationHelper.toVisualColumnSymbolsNumber(
        document.getCharsSequence(), foldRegion.getStartOffset(), myTargetOffset, cachedData.startX
      );
    }
    else {
      // Target offset is located on a different line with folding start.
      context.logicalColumn = myRepresentationHelper.toVisualColumnSymbolsNumber(
        document.getCharsSequence(), foldRegion.getStartOffset(), myTargetOffset, 0
      );
      context.softWrapColumnDiff = 0;
      int linesDiff = document.getLineNumber(myTargetOffset) - document.getLineNumber(foldRegion.getStartOffset());
      context.logicalLine += linesDiff;
      context.foldedLines += linesDiff;
      context.softWrapLinesBefore += context.softWrapLinesCurrent;
      context.softWrapLinesCurrent = 0;
    }

    context.foldingColumnDiff = context.visualColumn - context.softWrapColumnDiff - context.logicalColumn;
    context.offset = myTargetOffset;
    return context.buildLogicalPosition();
  }

  @Nullable
  @Override
  protected LogicalPosition buildIfExceeds(ProcessingContext context, TabData tabData) {
    if (tabData.offset == myTargetOffset) {
      return context.buildLogicalPosition();
    }
    return null;
  }

  @Nullable
  @Override
  public LogicalPosition processSoftWrap(ProcessingContext context, SoftWrap softWrap) {
    context.visualColumn = softWrap.getIndentInColumns();
    context.softWrapColumnDiff += softWrap.getIndentInColumns();
    if (softWrap.getStart() == myTargetOffset) {
      return context.buildLogicalPosition();
    }
    if (softWrap.getStart() == myCacheEntry.startOffset) {
      return null;
    }
    assert false;
    return context.buildLogicalPosition();
  }

  @NotNull
  @Override
  public LogicalPosition build(ProcessingContext context) {
    int diff = myTargetOffset - context.offset;
    context.logicalColumn += diff;
    context.visualColumn += diff;
    context.offset = myTargetOffset;
    return context.buildLogicalPosition();
  }
}
