/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FoldRegionImpl extends RangeMarkerImpl implements FoldRegion {
  private boolean myIsExpanded;
  private final EditorImpl myEditor;
  private final String myPlaceholderText;
  private final FoldingGroup myGroup;
  private final boolean myShouldNeverExpand;
  private boolean myDocumentRegionWasChanged;

  FoldRegionImpl(@NotNull EditorImpl editor,
                 int startOffset,
                 int endOffset,
                 @NotNull String placeholder,
                 @Nullable FoldingGroup group,
                 boolean shouldNeverExpand) {
    super(editor.getDocument(), startOffset, endOffset,false);
    myGroup = group;
    myShouldNeverExpand = shouldNeverExpand;
    myIsExpanded = true;
    myEditor = editor;
    myPlaceholderText = placeholder;
  }

  @Override
  public boolean isExpanded() {
    return myIsExpanded;
  }

  @Override
  public void setExpanded(boolean expanded) {
    setExpanded(expanded, true);
  }

  void setExpanded(boolean expanded, boolean notify) {
    FoldingModelImpl foldingModel = myEditor.getFoldingModel();
    if (myGroup == null) {
      doSetExpanded(expanded, foldingModel, this, notify);
    } else {
      for (final FoldRegion region : foldingModel.getGroupedRegions(myGroup)) {
        doSetExpanded(expanded, foldingModel, region, notify || region != this);
        // There is a possible case that we can't change expanded status of particular fold region (e.g. we can't collapse
        // if it contains caret). So, we revert all changes for the fold regions from the same group then.
        if (region.isExpanded() != expanded) {
          for (FoldRegion regionToRevert : foldingModel.getGroupedRegions(myGroup)) {
            if (regionToRevert == region) {
              break;
            }
            doSetExpanded(!expanded, foldingModel, regionToRevert, notify || region != this);
          }
          return;
        }
      }
    }
  }

  private static void doSetExpanded(boolean expanded, FoldingModelImpl foldingModel, FoldRegion region, boolean notify) {
    if (expanded) {
      foldingModel.expandFoldRegion(region, notify);
    }
    else{
      foldingModel.collapseFoldRegion(region, notify);
    }
  }

  @Override
  public boolean isValid() {
    return super.isValid() && intervalStart() < intervalEnd();
  }

  void setExpandedInternal(boolean toExpand) {
    myIsExpanded = toExpand;
  }

  @Override
  @NotNull
  public String getPlaceholderText() {
    return myPlaceholderText;
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  @Nullable
  public FoldingGroup getGroup() {
    return myGroup;
  }

  @Override
  public boolean shouldNeverExpand() {
    return myShouldNeverExpand;
  }
  
  boolean hasDocumentRegionChanged() {
    return myDocumentRegionWasChanged;
  }
  
  void resetDocumentRegionChanged() {
    myDocumentRegionWasChanged = false;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    if (isValid()) {
      int oldStart = intervalStart();
      int oldEnd = intervalEnd();
      int changeStart = e.getOffset();
      int changeEnd = e.getOffset() + e.getOldLength();
      if (changeStart < oldEnd && changeEnd > oldStart) myDocumentRegionWasChanged = true;
    }
    super.changedUpdateImpl(e);
    if (isValid()) {
      alignToSurrogateBoundaries();
    }
    myEditor.getFoldingModel().clearCachedValues();
  }

  @Override
  protected void onReTarget(int startOffset, int endOffset, int destOffset) {
    alignToSurrogateBoundaries();
  }

  private void alignToSurrogateBoundaries() {
    Document document = getDocument();
    int start = intervalStart();
    int end = intervalEnd();
    if (DocumentUtil.isInsideSurrogatePair(document, start)) {
      setIntervalStart(start - 1);
    }
    if (DocumentUtil.isInsideSurrogatePair(document, end)) {
      setIntervalEnd(end - 1);
    }
  }

  @Override
  public void dispose() {
    myEditor.getFoldingModel().removeRegionFromTree(this);
  }

  @Override
  public String toString() {
    return "FoldRegion " + (myIsExpanded ? "-" : "+") + "(" + getStartOffset() + ":" + getEndOffset() + ")"
           + (isValid() ? "" : "(invalid)") + ", placeholder='" + myPlaceholderText + "'";
  }
}
