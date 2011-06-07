/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.util.Key;

import java.util.Iterator;

public class MergeSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.MergeSearchHelper");
  private static final Key[] ourMergeListKeys = new Key[]{Key.create("leftMergeSearchHelper"),
                                                    Key.create("centerMergeSearchHelper"),
                                                    Key.create("rightMergeSearchHelper")};
  private final MergeList myMergeList;
  private final int myIndex;

  private MergeSearchHelper(MergeList mergeList, int index) {
    myMergeList = mergeList;
    myIndex = index;
  }

  private static MergeSearchHelper forMergeList(MergeList mergeList, int contentIndex) {
    Key<MergeSearchHelper> key = (Key<MergeSearchHelper>)ourMergeListKeys[contentIndex];
    MergeSearchHelper helper = mergeList.getUserData(key);
    if (helper == null) {
      helper = new MergeSearchHelper(mergeList, contentIndex);
      mergeList.putUserData(key, helper);
    }
    return helper;
  }

  public static Change findChangeAt(EditorMouseEvent e, MergePanel2 mergePanel, int index) {
    if (mergePanel.getMergeList() == null) return null;
    Editor editor = e.getEditor();
    LOG.assertTrue(editor == mergePanel.getEditor(index));
    LogicalPosition logicalPosition = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
    int offset = editor.logicalPositionToOffset(logicalPosition);
    return forMergeList(mergePanel.getMergeList(), index).findChangeAt(offset);
  }

  private Change findChangeAt(int offset) {
    for (int i = 0; i < 2; i++) {
      Change change = changeAtOffset(myMergeList.getChanges(FragmentSide.fromIndex(i)).getChanges().iterator(), offset);
      if (change != null) return change;
    }
    return null;
  }

  private Change changeAtOffset(Iterator<Change> iterator, int offset) {
    while (iterator.hasNext()) {
      Change change = iterator.next();
      FragmentSide side = chooseInterestedSide(change);
      if (side == null) continue;
      if (change.getChangeSide(side).contains(offset)) return change;
    }
    return null;

  }

  private FragmentSide chooseInterestedSide(Change change) {
    if (myIndex == 1) return MergeList.BASE_SIDE;
    if (myIndex == 0)
      if (change.getChangeList() != myMergeList.getChanges(FragmentSide.SIDE1)) return null;
    if (myIndex == 2)
      if (change.getChangeList() != myMergeList.getChanges(FragmentSide.SIDE2)) return null;
    return MergeList.BRANCH_SIDE;
  }

  public static Change findChangeAt(CaretEvent e, MergePanel2 mergePanel, int index) {
    Editor editor = e.getEditor();
    LOG.assertTrue(editor == mergePanel.getEditor(index));
    return forMergeList(mergePanel.getMergeList(), index).
      findChangeAt(editor.logicalPositionToOffset(e.getNewPosition()));
  }
}
