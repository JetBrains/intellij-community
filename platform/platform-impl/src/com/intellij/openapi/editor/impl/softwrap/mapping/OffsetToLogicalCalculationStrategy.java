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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
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

  OffsetToLogicalCalculationStrategy(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull List<CacheEntry> cache)
  {
    super(editor, storage, cache);
  }

  public void init(final int targetOffset, final List<CacheEntry> cache) {
    reset();

    myTargetOffset = targetOffset;
    Document document = myEditor.getDocument();
    if (targetOffset == 0) {
      LogicalPosition eager = new LogicalPosition(0, 0, 0, 0, 0, 0, 0);
      setEagerMatch(eager);
      return;
    }
    else if (targetOffset >= document.getTextLength()) {
      if (cache.isEmpty()) {
        setFirstInitialPosition();
        return;
      }
      else {
        // We expect the following possible cases here:
        //   1. There is a cache entry for the target line;
        //      1.1. Document ends by line feed;
        //      1.2. Document ends by the symbol that is not line feed;
        //   2. There is no cache entry for the target line;;
        
        CacheEntry lastEntry = cache.get(cache.size() - 1);
        if (lastEntry.endOffset >= targetOffset - 1) {
          EditorPosition position = lastEntry.buildEndLinePosition();
          if (document.getCharsSequence().charAt(document.getTextLength() - 1) == '\n') {
            position.onNewLineSoftWrapAware();
          }
          setEagerMatch(position.buildLogicalPosition());
          return;
        }
      }
    } else if (cache.size() > 0 && cache.get(cache.size() - 1).endOffset < targetOffset) {
      EditorPosition position = cache.get(cache.size() - 1).buildEndLinePosition();
      position.onNewLineSoftWrapAware();
      setInitialPosition(position);
      return;
    }

    int i = MappingUtil.getCacheEntryIndexForOffset(targetOffset, myEditor.getDocument(), cache);
    CacheEntry cacheEntry = null;
    if (i >= 0) {
      CacheEntry candidate = cache.get(i);
      if (candidate.startOffset <= targetOffset) {
        cacheEntry = candidate;
      }
    }
    else if (i < -1) {
      i = -i - 2;
      if (i < myCache.size()) {
        cacheEntry = myCache.get(i);
      }
    }
    
    if (cacheEntry == null) {
      setFirstInitialPosition();
    }
    else if (cacheEntry.startOffset <= targetOffset && cacheEntry.endOffset >= targetOffset) {
      setTargetEntry(cacheEntry, true);
    }
    else {
      setInitialPosition(cacheEntry.buildStartLinePosition());
    }
  }

  @Override
  protected LogicalPosition buildIfExceeds(EditorPosition position, int offset) {
    if (myTargetOffset >= offset) {
      return null;
    }

    Document document = myEditor.getDocument();
    int logicalLine = document.getLineNumber(myTargetOffset);
    int linesDiff = logicalLine - position.logicalLine;
    if (linesDiff > 0) {
      position.onNewLine();
      int column = myTargetOffset - document.getLineStartOffset(logicalLine);
      position.visualColumn = column;
      position.logicalColumn = column;
    }
    else {
      int columnsDiff = myTargetOffset - position.offset;
      position.logicalColumn += columnsDiff;
      position.visualColumn += columnsDiff;
    }
    position.logicalLine = logicalLine;
    position.offset = myTargetOffset;
    
    return position.buildLogicalPosition();
  }

  @Override
  protected LogicalPosition buildIfExceeds(@NotNull EditorPosition position, @NotNull FoldRegion foldRegion) {
    if (myTargetOffset >= foldRegion.getEndOffset()) {
      return null;
    }

    Document document = myEditor.getDocument();
    int targetLogicalLine = document.getLineNumber(myTargetOffset);
    if (targetLogicalLine == position.logicalLine) {
      // Target offset is located on the same logical line as folding start.
      position.logicalColumn += SoftWrapModelImpl.getEditorTextRepresentationHelper(myEditor).toVisualColumnSymbolsNumber(
        foldRegion.getStartOffset(), myTargetOffset, position.x
      );
    }
    else {
      // Target offset is located on a different line with folding start.
      position.logicalColumn = SoftWrapModelImpl.getEditorTextRepresentationHelper(myEditor).toVisualColumnSymbolsNumber(
        foldRegion.getStartOffset(), myTargetOffset, 0
      );
      position.softWrapColumnDiff = 0;
      int linesDiff = document.getLineNumber(myTargetOffset) - document.getLineNumber(foldRegion.getStartOffset());
      position.logicalLine += linesDiff;
      position.foldedLines += linesDiff;
      position.softWrapLinesBefore += position.softWrapLinesCurrent;
      position.softWrapLinesCurrent = 0;
    }

    position.foldingColumnDiff = position.visualColumn - position.softWrapColumnDiff - position.logicalColumn;
    position.offset = myTargetOffset;
    return position.buildLogicalPosition();
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
  public LogicalPosition processSoftWrap(@NotNull EditorPosition position, SoftWrap softWrap) {
    position.visualColumn = softWrap.getIndentInColumns();
    position.softWrapColumnDiff += softWrap.getIndentInColumns();
    if (softWrap.getStart() == myTargetOffset) {
      return position.buildLogicalPosition();
    }
    else {
      return null;
    }
  }

  @NotNull
  @Override
  public LogicalPosition build(@NotNull EditorPosition position) {
    Document document = myEditor.getDocument();
    int logicalLine = document.getLineNumber(myTargetOffset);
    int linesDiff = logicalLine - position.logicalLine;
    if (linesDiff > 0) {
      position.onNewLine();
      position.logicalLine = logicalLine;
      int column = myTargetOffset - document.getLineStartOffset(logicalLine);
      position.logicalColumn = column;
      position.visualColumn = column;
    }
    else {
      int columnsDiff = myTargetOffset - position.offset;
      position.logicalColumn += columnsDiff;
      position.visualColumn += columnsDiff;
    }
    position.offset = myTargetOffset;
    return position.buildLogicalPosition();
  }
}
