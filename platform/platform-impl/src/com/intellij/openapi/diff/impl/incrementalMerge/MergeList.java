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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.diff.impl.processing.DiffPolicy;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.diff.impl.util.ContextLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MergeList implements UserDataHolder {
  private static final Logger LOG = Logger.getInstance(MergeList.class);

  public static final FragmentSide BRANCH_SIDE = FragmentSide.SIDE2;
  public static final FragmentSide BASE_SIDE = FragmentSide.SIDE1;

  public static final DataKey<MergeList> DATA_KEY = DataKey.create("mergeList");
  public static final Condition<Change> NOT_CONFLICTS = change -> !(change instanceof ConflictChange);

  @NotNull private final UserDataHolderBase myDataHolder = new UserDataHolderBase();
  @NotNull private final ChangeList myBaseToLeftChangeList;
  @NotNull private final ChangeList myBaseToRightChangeList;
  @Nullable private final String myErrorMessage;

  private MergeList(@Nullable Project project,
                    @NotNull Document left,
                    @NotNull Document base,
                    @NotNull Document right,
                    @Nullable String errorMessage) {
    myBaseToLeftChangeList = new ChangeList(base, left, project);
    myBaseToRightChangeList = new ChangeList(base, right, project);
    myErrorMessage = errorMessage;
  }

  @NotNull
  public ChangeList getLeftChangeList() {
    return myBaseToLeftChangeList;
  }

  @NotNull
  public ChangeList getRightChangeList() {
    return myBaseToRightChangeList;
  }

  @NotNull
  public static MergeList create(@Nullable Project project, @NotNull Document left, @NotNull Document base, @NotNull Document right) {
    MergeList mergeList;
    String leftText = left.getText();
    String baseText = base.getText();
    String rightText = right.getText();
    @NonNls final Object[] data = {
      "Left\n", leftText,
      "\nBase\n", baseText,
      "\nRight\n", rightText
    };
    ContextLogger logger = new ContextLogger(LOG, new ContextLogger.SimpleContext(data));
    List<MergeFragment> fragmentList;
    try {
      fragmentList = processText(leftText, baseText, rightText, logger);
      mergeList = new MergeList(project, left, base, right, null);
    }
    catch (FilesTooBigForDiffException e) {
      fragmentList = Collections.emptyList();
      mergeList = new MergeList(project, left, base, right, e.getMessage());
    }

    ArrayList<Change> leftChanges = new ArrayList<>();
    ArrayList<Change> rightChanges = new ArrayList<>();
    for (MergeFragment mergeFragment : fragmentList) {
      TextRange baseRange = mergeFragment.getBase();
      TextRange leftRange = mergeFragment.getLeft();
      TextRange rightRange = mergeFragment.getRight();

      if (compareSubstring(leftText, leftRange, rightText, rightRange)) {
        MergeNoConflict conflict = new MergeNoConflict(baseRange, leftRange, rightRange, mergeList);
        assert conflict.getLeftChange() != null;
        assert conflict.getRightChange() != null;
        leftChanges.add(conflict.getLeftChange());
        rightChanges.add(conflict.getRightChange());
      }
      else if (compareSubstring(baseText, baseRange, leftText, leftRange)) {
        rightChanges.add(SimpleChange.fromRanges(baseRange, rightRange, mergeList.myBaseToRightChangeList));
      }
      else if (compareSubstring(baseText, baseRange, rightText, rightRange)) {
        leftChanges.add(SimpleChange.fromRanges(baseRange, leftRange, mergeList.myBaseToLeftChangeList));
      }
      else {
        MergeConflict conflict = new MergeConflict(baseRange, leftRange, rightRange, mergeList);
        assert conflict.getLeftChange() != null;
        assert conflict.getRightChange() != null;
        leftChanges.add(conflict.getLeftChange());
        rightChanges.add(conflict.getRightChange());
      }
    }
    mergeList.myBaseToLeftChangeList.setChanges(leftChanges);
    mergeList.myBaseToRightChangeList.setChanges(rightChanges);
    return mergeList;
  }

  private static boolean compareSubstring(@NotNull String text1,
                                          @NotNull TextRange range1,
                                          @NotNull String text2,
                                          @NotNull TextRange range2) {
    if (range1.getLength() != range2.getLength()) return false;

    int index1 = range1.getStartOffset();
    int index2 = range2.getStartOffset();
    while (index1 < range1.getEndOffset()) {
      if (text1.charAt(index1) != text2.charAt(index2)) return false;
      index1++;
      index2++;
    }
    return true;
  }

  @NotNull
  private static List<MergeFragment> processText(@NotNull String leftText,
                                                 @NotNull String baseText,
                                                 @NotNull String rightText,
                                                 @NotNull ContextLogger logger) throws FilesTooBigForDiffException {
    DiffFragment[] leftFragments = DiffPolicy.DEFAULT_LINES.buildFragments(DiffString.create(baseText), DiffString.create(leftText));
    DiffFragment[] rightFragments = DiffPolicy.DEFAULT_LINES.buildFragments(DiffString.create(baseText), DiffString.create(rightText));
    int[] leftOffsets = {0, 0};
    int[] rightOffsets = {0, 0};
    int leftIndex = 0;
    int rightIndex = 0;
    MergeBuilder builder = new MergeBuilder(logger);
    while (leftIndex < leftFragments.length || rightIndex < rightFragments.length) {
      FragmentSide side;
      TextRange[] equalRanges = new TextRange[2];
      if (leftOffsets[0] < rightOffsets[0] && leftIndex < leftFragments.length) {
        side = FragmentSide.SIDE1;
        getEqualRanges(leftFragments[leftIndex], leftOffsets, equalRanges);
        leftIndex++;
      } else if (rightIndex < rightFragments.length) {
        side = FragmentSide.SIDE2;
        getEqualRanges(rightFragments[rightIndex], rightOffsets, equalRanges);
        rightIndex++;
      } else break;
      if (equalRanges[0] != null && equalRanges[1] != null) builder.add(equalRanges[0], equalRanges[1], side);
      else logger.assertTrue(equalRanges[0] == null && equalRanges[1] == null);
    }
    return builder.finish(leftText.length(), baseText.length(), rightText.length());
  }

  private static void getEqualRanges(@NotNull DiffFragment fragment, @NotNull int[] leftOffsets, @NotNull TextRange[] equalRanges) {
    int baseLength = getTextLength(fragment.getText1());
    int versionLength = getTextLength(fragment.getText2());
    if (fragment.isEqual()) {
      equalRanges[0] = new TextRange(leftOffsets[0], leftOffsets[0] + baseLength);
      equalRanges[1] = new TextRange(leftOffsets[1], leftOffsets[1] + versionLength);
    } else {
      equalRanges[0] = null;
      equalRanges[1] = null;
    }
    leftOffsets[0] += baseLength;
    leftOffsets[1] += versionLength;
  }

  private static int getTextLength(@Nullable DiffString text1) {
    return text1 != null ? text1.length() : 0;
  }

  public static MergeList create(@NotNull DiffRequest data) {
    DiffContent[] contents = data.getContents();
    return create(data.getProject(), contents[0].getDocument(), contents[1].getDocument(), contents[2].getDocument());
  }

  public void setMarkups(Editor left, Editor base, Editor right) {
    myBaseToLeftChangeList.setMarkup(base, left);
    myBaseToRightChangeList.setMarkup(base, right);
    addActions(FragmentSide.SIDE1);
    addActions(FragmentSide.SIDE2);
  }

  public Iterator<Change> getAllChanges() {
    return ContainerUtil.concatIterators(myBaseToLeftChangeList.getChanges().iterator(), myBaseToRightChangeList.getChanges().iterator());
  }

  public void addListener(ChangeList.Listener listener) {
    myBaseToLeftChangeList.addListener(listener);
    myBaseToRightChangeList.addListener(listener);
  }

  public void removeListener(ChangeList.Listener listener) {
    myBaseToLeftChangeList.removeListener(listener);
    myBaseToRightChangeList.removeListener(listener);
  }

  private void addActions(@NotNull final FragmentSide side) {
    ChangeList changeList = getChanges(side);
    final FragmentSide originalSide = BRANCH_SIDE;
    for (int i = 0; i < changeList.getCount(); i++) {
      final Change change = changeList.getChange(i);
      if (!change.canHasActions(originalSide)) continue;
      Icon arrowIcon = side == FragmentSide.SIDE1 ? AllIcons.Diff.ArrowRight : AllIcons.Diff.Arrow;
      AnAction applyAction = new DumbAwareAction(DiffBundle.message("merge.dialog.apply.change.action.name"), null, arrowIcon) {
        @Override
        public void actionPerformed(@Nullable AnActionEvent e) {
          apply(change);
        }
      };
      AnAction ignoreAction = new DumbAwareAction(DiffBundle.message("merge.dialog.ignore.change.action.name"), null, AllIcons.Diff.Remove) {
        @Override
        public void actionPerformed(@Nullable AnActionEvent e) {
          change.removeFromList();
        }
      };
      change.getChangeSide(originalSide).getHighlighterHolder().setActions(new AnAction[]{applyAction, ignoreAction});
    }
  }

  private static void apply(final Change change) {
    Change.apply(change, BRANCH_SIDE);
  }

  @NotNull
  public ChangeList getChanges(@NotNull final FragmentSide changesSide) {
    if (changesSide == FragmentSide.SIDE1) {
      return myBaseToLeftChangeList;
    }
    else {
      return myBaseToRightChangeList;
    }
  }

  public void removeChanges(@Nullable Change leftChange, @Nullable Change rightChange) {
    if (leftChange != null) {
      myBaseToLeftChangeList.remove(leftChange);
    }
    if (rightChange != null) {
      myBaseToRightChangeList.remove(rightChange);
    }
  }

  public Document getBaseDocument() {
    Document document = myBaseToLeftChangeList.getDocument(BASE_SIDE);
    LOG.assertTrue(document == myBaseToRightChangeList.getDocument(BASE_SIDE));
    return document;
  }

  @Nullable
  public static MergeList fromDataContext(DataContext dataContext) {
    MergeList mergeList = DATA_KEY.getData(dataContext);
    if (mergeList != null) return mergeList;
    MergePanel2 mergePanel = MergePanel2.fromDataContext(dataContext);
    return mergePanel == null ? null : mergePanel.getMergeList();
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    myDataHolder.putUserData(key, value);
  }

  @NotNull
  public FragmentSide getSideOf(@NotNull ChangeList source) {
    if (myBaseToLeftChangeList == source) {
      return FragmentSide.SIDE1;
    }
    else {
      return FragmentSide.SIDE2;
    }
  }

  public void updateMarkup() {
    myBaseToLeftChangeList.updateMarkup();
    myBaseToRightChangeList.updateMarkup();
  }

  @Nullable
  public String getErrorMessage() {
    return myErrorMessage;
  }

  public void startBulkUpdate() {
    Document document1 = myBaseToLeftChangeList.getDocument(BRANCH_SIDE);
    Document document2 = myBaseToRightChangeList.getDocument(BRANCH_SIDE);
    Document document3 = myBaseToLeftChangeList.getDocument(BASE_SIDE);
    assert document3 == myBaseToRightChangeList.getDocument(BASE_SIDE);

    ((DocumentEx)document1).setInBulkUpdate(true);
    ((DocumentEx)document2).setInBulkUpdate(true);
    ((DocumentEx)document3).setInBulkUpdate(true);
  }

  public void finishBulkUpdate() {
    Document document1 = myBaseToLeftChangeList.getDocument(BRANCH_SIDE);
    Document document2 = myBaseToRightChangeList.getDocument(BRANCH_SIDE);
    Document document3 = myBaseToLeftChangeList.getDocument(BASE_SIDE);
    assert document3 == myBaseToRightChangeList.getDocument(BASE_SIDE);

    ((DocumentEx)document1).setInBulkUpdate(false);
    ((DocumentEx)document2).setInBulkUpdate(false);
    ((DocumentEx)document3).setInBulkUpdate(false);
  }
}
