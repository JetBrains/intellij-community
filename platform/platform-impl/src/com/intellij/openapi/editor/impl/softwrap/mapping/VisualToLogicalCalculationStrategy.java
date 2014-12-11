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
import com.intellij.openapi.editor.impl.softwrap.SoftWrapsStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
* @author Denis Zhdanov
* @since Sep 9, 2010 9:20:44 AM
*/
class VisualToLogicalCalculationStrategy extends AbstractMappingStrategy<LogicalPosition> {

  private final CacheEntry mySearchKey;
  private VisualPosition myTargetVisual;

  VisualToLogicalCalculationStrategy(@NotNull EditorEx editor, @NotNull SoftWrapsStorage storage, @NotNull List<CacheEntry> cache)
  {
    super(editor, storage, cache);
    mySearchKey = new CacheEntry(0, editor);
  }

  public void init(@NotNull final VisualPosition targetVisual, @NotNull final List<CacheEntry> cache) {
    reset();

    myTargetVisual = targetVisual;
    mySearchKey.visualLine = targetVisual.line;
    int i = Collections.binarySearch(cache, mySearchKey);
    if (i >= 0) {
      CacheEntry cacheEntry = cache.get(i);
      if (cacheEntry.visualLine == targetVisual.line) {
        if (targetVisual.column == 0) {
          setEagerMatch(cacheEntry.buildStartLinePosition().buildLogicalPosition());
        }
        else if (targetVisual.column == cacheEntry.endVisualColumn) {
          setEagerMatch(cacheEntry.buildEndLinePosition().buildLogicalPosition());
        }
        else if (targetVisual.column > cacheEntry.endVisualColumn) {
          EditorPosition position = cacheEntry.buildEndLinePosition();
          int columnsDiff = targetVisual.column - cacheEntry.endVisualColumn;
          position.visualColumn += columnsDiff;
          if (myStorage.getSoftWrap(cacheEntry.endOffset) == null) {
            position.logicalColumn += columnsDiff;
          }
          else {
            position.softWrapColumnDiff += columnsDiff;
          }
          setEagerMatch(position.buildLogicalPosition());
        }
        else {
          setTargetEntry(cacheEntry, true);
        }
      }
      else {
        setInitialPosition(cacheEntry.buildStartLinePosition());
      }
      return;
    }
    
    i = -i - 1;
    if (i > 0 && i <= cache.size()) {
      CacheEntry entry = cache.get(i - 1);
      EditorPosition position = entry.buildEndLinePosition();
      position.onNewLineSoftWrapAware();
      setInitialPosition(position);
    }
    else {
      setFirstInitialPosition();
    }
  }

  @Override
  protected LogicalPosition buildIfExceeds(EditorPosition position, int offset) {
    Document document = myEditor.getDocument();
    
    // There is a possible case that target visual line starts with soft wrap. We want to process that at 'processSoftWrap()' method.
    CacheEntry targetEntry = getTargetEntry();
    if (targetEntry != null && targetEntry.startOffset == offset && myStorage.getSoftWrap(offset) != null) {
      return null;
    }

    int i = MappingUtil.getCacheEntryIndexForOffset(offset, document, myCache);
    if (i < 0) {
      return null;
    }

    CacheEntry cacheEntry = myCache.get(i);
    if (cacheEntry.visualLine < myTargetVisual.line) {
      return null;
    }
    
    // Return eagerly if target visual position remains between current context position and the one defined by the given offset.

    if (offset < cacheEntry.startOffset || myTargetVisual.line < cacheEntry.visualLine 
        || (position.visualColumn + offset - position.offset >= myTargetVisual.column)) 
    {
      int linesDiff = myTargetVisual.line - position.visualLine;
      if (linesDiff > 0) {
        position.logicalColumn = myTargetVisual.column;
        position.offset = document.getLineStartOffset(position.logicalLine + linesDiff);
        position.offset += myTargetVisual.column;
      }
      else {
        int columnsDiff = myTargetVisual.column - position.visualColumn;
        position.logicalColumn += columnsDiff;
        position.offset += columnsDiff;
      }
      
      // There is a possible case that target visual position points to virtual space after line end. We need
      // to define correct offset then
      position.offset = Math.min(position.offset, document.getLineEndOffset(position.logicalLine));
      
      position.visualLine += linesDiff;
      position.visualColumn = myTargetVisual.column;
      position.logicalLine += linesDiff;
      return position.buildLogicalPosition();
    }

    return null;
  }

  @Nullable
  @Override
  public LogicalPosition processSoftWrap(@NotNull EditorPosition position, SoftWrap softWrap) {
    // There is a possible case that current visual line starts with soft wrap and target visual position points to its
    // virtual space.
    CacheEntry targetEntry = getTargetEntry();
    if (targetEntry != null && targetEntry.startOffset == softWrap.getStart()) {
      if (myTargetVisual.column <= softWrap.getIndentInColumns()) {
        EditorPosition resultingContext = targetEntry.buildStartLinePosition();
        resultingContext.visualColumn = myTargetVisual.column;
        resultingContext.softWrapColumnDiff += myTargetVisual.column;
        return resultingContext.buildLogicalPosition();
      }
      else {
        position.visualColumn = softWrap.getIndentInColumns();
        position.softWrapColumnDiff += softWrap.getIndentInColumns();
        return null;
      }
    }
    
    return null;
  }

  @Override
  protected LogicalPosition buildIfExceeds(@NotNull EditorPosition context, @NotNull FoldRegion foldRegion) {
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
  protected LogicalPosition buildIfExceeds(EditorPosition context, TabData tabData) {
    if (context.visualColumn + tabData.widthInColumns >= myTargetVisual.column) {
      context.logicalColumn += myTargetVisual.column - context.visualColumn;
      context.visualColumn = myTargetVisual.column;
      return context.buildLogicalPosition();
    }
    return null;
  }

  @NotNull
  @Override
  public LogicalPosition build(@NotNull EditorPosition position) {
    int linesDiff = myTargetVisual.line - position.visualLine;
    if (linesDiff <= 0) {
      int columnsDiff = myTargetVisual.column - position.visualColumn;
      position.logicalColumn += columnsDiff;
      position.offset += columnsDiff;
    }
    else {
      position.logicalLine += linesDiff;
      position.visualLine += linesDiff;
      Document document = myEditor.getDocument();
      if (document.getLineCount() <= position.logicalLine) {
        position.offset = Math.max(0, document.getTextLength() - 1);
      }
      else {
        int startLineOffset = document.getLineStartOffset(position.logicalLine);
        position.offset = startLineOffset + myTargetVisual.column;
      }
      position.logicalColumn = myTargetVisual.column;
    }
    
    position.visualColumn = myTargetVisual.column;
    return position.buildLogicalPosition();
  }
}
