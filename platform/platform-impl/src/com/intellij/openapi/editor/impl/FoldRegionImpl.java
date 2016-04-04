/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 22, 2002
 * Time: 5:51:22 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FoldRegionImpl extends RangeMarkerImpl implements FoldRegion {
  private boolean myIsExpanded;
  private final Editor myEditor;
  private final String myPlaceholderText;
  private final FoldingGroup myGroup;
  private final boolean myShouldNeverExpand;
  private boolean myDocumentRegionWasChanged;

  FoldRegionImpl(@NotNull Editor editor,
                 int startOffset,
                 int endOffset,
                 @NotNull String placeholder,
                 @Nullable FoldingGroup group,
                 boolean shouldNeverExpand) {
    super((DocumentEx)editor.getDocument(), startOffset, endOffset,true);
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
    FoldingModelImpl foldingModel = (FoldingModelImpl)myEditor.getFoldingModel();
    if (myGroup == null) {
      doSetExpanded(expanded, foldingModel, this);
    } else {
      for (final FoldRegion region : foldingModel.getGroupedRegions(myGroup)) {
        doSetExpanded(expanded, foldingModel, region);
        // There is a possible case that we can't change expanded status of particular fold region (e.g. we can't collapse
        // if it contains caret). So, we revert all changes for the fold regions from the same group then.
        if (region.isExpanded() != expanded) {
          for (FoldRegion regionToRevert : foldingModel.getGroupedRegions(myGroup)) {
            if (regionToRevert == region) {
              break;
            }
            doSetExpanded(!expanded, foldingModel, regionToRevert);
          }
          return;
        }
      }
    }
  }

  private static void doSetExpanded(boolean expanded, FoldingModelImpl foldingModel, FoldRegion region) {
    if (expanded) {
      foldingModel.expandFoldRegion(region);
    }
    else{
      foldingModel.collapseFoldRegion(region);
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
  }

  @Override
  public String toString() {
    return "FoldRegion " + (myIsExpanded ? "-" : "+") + "(" + getStartOffset() + ":" + getEndOffset() + ")"
           + (isValid() ? "" : "(invalid)") + ", placeholder='" + myPlaceholderText + "'";
  }
}
