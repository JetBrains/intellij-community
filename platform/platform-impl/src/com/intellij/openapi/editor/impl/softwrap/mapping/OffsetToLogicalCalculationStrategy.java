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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author Denis Zhdanov
* @since Sep 9, 2010 9:20:55 AM
*/
class OffsetToLogicalCalculationStrategy extends AbstractMappingStrategy<LogicalPosition> {

  private int myTargetOffset;

  OffsetToLogicalCalculationStrategy(final Editor editor, SoftWrapsStorage storage, EditorTextRepresentationHelper representationHelper) {
    super(editor, storage, representationHelper);
  }

  public void init(final int targetOffset, final List<CacheEntry> cache) {
    setEagerMatch(null);

    myTargetOffset = targetOffset;
    Document document = myEditor.getDocument();
    if (targetOffset == 0) {
      LogicalPosition eager = new LogicalPosition(0, 0, 0, 0, 0, 0, 0);
      setEagerMatch(eager);
      return;
    }
    else if (targetOffset >= document.getTextLength()) {
      if (cache.isEmpty()) {
        setEagerMatch(new LogicalPosition(0, 0, 0, 0, 0, 0, 0));
      }
      else {
        // We expect two possible cases here:
        //   1. Document ends by line feed;
        //   2. Document ends by the symbol that is not line feed;
        // We also expect the cache to contain corresponding entry for the visual line that lays after document text if it ends
        // by line feed. So, we increment column if the document doesn't end by line feed and use default one (zero) if it
        // ends by line feed.
        CacheEntry lastEntry = cache.get(cache.size() - 1);
        int columnToUse = lastEntry.endLogicalColumn;
        if (lastEntry.endOffset < targetOffset) {
          columnToUse++;
        }
        LogicalPosition eager = new LogicalPosition(
          lastEntry.endLogicalLine, columnToUse, lastEntry.endSoftWrapLinesBefore,
          lastEntry.endSoftWrapLinesCurrent, lastEntry.endSoftWrapColumnDiff, lastEntry.endFoldedLines,
          lastEntry.endFoldingColumnDiff
        );
        setEagerMatch(eager);
      }
      return;
    }

    int i = MappingUtil.getCacheEntryIndexForOffset(targetOffset, myEditor.getDocument(), cache);
    setCacheEntry(cache.get(i));
  }

  @Override
  protected LogicalPosition buildIfExceeds(EditorPosition context, int offset) {
    if (myTargetOffset > offset) {
      return null;
    }

    // Process use-case when target offset points to 'after soft wrap' position.
    SoftWrap softWrap = myStorage.getSoftWrap(offset);
    if (softWrap != null && offset < getCacheEntry().endOffset) {
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
  protected LogicalPosition buildIfExceeds(@NotNull EditorPosition context, @NotNull FoldRegion foldRegion) {
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
  protected LogicalPosition buildIfExceeds(EditorPosition context, TabData tabData) {
    if (tabData.offset == myTargetOffset) {
      return context.buildLogicalPosition();
    }
    return null;
  }

  @Nullable
  @Override
  public LogicalPosition processSoftWrap(EditorPosition position, SoftWrap softWrap) {
    position.visualColumn = softWrap.getIndentInColumns();
    position.softWrapColumnDiff += softWrap.getIndentInColumns();
    if (softWrap.getStart() == myTargetOffset) {
      return position.buildLogicalPosition();
    }
    if (softWrap.getStart() == getCacheEntry().startOffset) {
      return null;
    }
    assert false;
    return position.buildLogicalPosition();
  }

  @NotNull
  @Override
  public LogicalPosition build(EditorPosition position) {
    int diff = myTargetOffset - position.offset;
    position.logicalColumn += diff;
    position.visualColumn += diff;
    position.offset = myTargetOffset;
    return position.buildLogicalPosition();
  }
}
