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
import com.intellij.openapi.diff.impl.util.ContextLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MergeList implements UserDataHolder {

  public static final FragmentSide BRANCH_SIDE = FragmentSide.SIDE2;
  public static final FragmentSide BASE_SIDE = FragmentSide.SIDE1;

  public static final DataKey<MergeList> DATA_KEY = DataKey.create("mergeList");
  public static final Condition<Change> NOT_CONFLICTS = new Condition<Change>() {
    public boolean value(Change change) {
      return !(change instanceof ConflictChange);
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.MergeList");

  @NotNull private final UserDataHolderBase myDataHolder = new UserDataHolderBase();
  @NotNull private final ChangeList myBaseToLeftChangeList;
  @NotNull private final ChangeList myBaseToRightChangeList;

  private MergeList(@Nullable Project project, @NotNull Document left, @NotNull Document base, @NotNull Document right) {
    myBaseToLeftChangeList = new ChangeList(base, left, project);
    myBaseToRightChangeList = new ChangeList(base, right, project);
  }

  @NotNull
  public ChangeList getLeftChangeList() {
    return myBaseToLeftChangeList;
  }

  @NotNull
  public ChangeList getRightChangeList() {
    return myBaseToRightChangeList;
  }

  public static MergeList create(@Nullable Project project, @NotNull Document left, @NotNull Document base,
                                 @NotNull Document right) throws FilesTooBigForDiffException {
    MergeList mergeList = new MergeList(project, left, base, right);
    String leftText = left.getText();
    String baseText = base.getText();
    String rightText = right.getText();
    @NonNls final Object[] data = {
      "Left\n", leftText,
      "\nBase\n", baseText,
      "\nRight\n", rightText
    };
    ContextLogger logger = new ContextLogger(LOG, new ContextLogger.SimpleContext(data));
    List<MergeFragment> fragmentList = processText(leftText, baseText, rightText, logger);

    ArrayList<Change> leftChanges = new ArrayList<Change>();
    ArrayList<Change> rightChanges = new ArrayList<Change>();
    for (Iterator<MergeFragment> fragmentIterator = fragmentList.iterator(); fragmentIterator.hasNext(); ) {
      final MergeFragment mergeFragment = fragmentIterator.next();
      TextRange baseRange = mergeFragment.getBase();
      TextRange leftRange = mergeFragment.getLeft();
      TextRange rightRange = mergeFragment.getRight();

      if (leftRange == null) {
        if (rightRange == null) {
          if (!fragmentIterator.hasNext() && baseRange.getEndOffset() == baseText.length()) {
            // the very end, both local and remote revisions does not contain latest base fragment
            final int rightTextLength = rightText.length();
            final int leftTextLength = leftText.length();
            rightChanges.add(SimpleChange.fromRanges(baseRange, new TextRange(rightTextLength, rightTextLength), mergeList.myBaseToRightChangeList));
            leftChanges.add(SimpleChange.fromRanges(baseRange, new TextRange(leftTextLength, leftTextLength), mergeList.myBaseToLeftChangeList));
          } else {
            LOG.error("Left Text: " + leftText + "\n" + "Right Text: " + rightText + "\nBase Text: " + baseText);
          }
        } else {
          rightChanges.add(SimpleChange.fromRanges(baseRange, rightRange, mergeList.myBaseToRightChangeList));
        }
      }
      else if (rightRange == null) {
        leftChanges.add(SimpleChange.fromRanges(baseRange, leftRange, mergeList.myBaseToLeftChangeList));
      }
      else {
        MergeConflict conflict = new MergeConflict(baseRange, mergeList, leftRange, rightRange);
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

  private static List<MergeFragment> processText(String leftText, String baseText, String rightText,
                                                              ContextLogger logger) throws FilesTooBigForDiffException {
    DiffFragment[] leftFragments = DiffPolicy.DEFAULT_LINES.buildFragments(baseText, leftText);
    DiffFragment[] rightFragments = DiffPolicy.DEFAULT_LINES.buildFragments(baseText, rightText);
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

  private static void getEqualRanges(DiffFragment fragment, int[] leftOffsets, TextRange[] equalRanges) {
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

  private static int getTextLength(String text1) {
    return text1 != null ? text1.length() : 0;
  }

  public static MergeList create(DiffRequest data) throws FilesTooBigForDiffException {
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

  private void addActions(final FragmentSide side) {
    ChangeList changeList = getChanges(side);
    final FragmentSide originalSide = BRANCH_SIDE;
    for (int i = 0; i < changeList.getCount(); i++) {
      final Change change = changeList.getChange(i);
      if (!change.canHasActions(originalSide)) continue;
      AnAction applyAction = new AnAction(DiffBundle.message("merge.dialog.apply.change.action.name"), null, AllIcons.Diff.Arrow) {
        public void actionPerformed(AnActionEvent e) {
          apply(change);
        }
      };
      AnAction ignoreAction = new AnAction(DiffBundle.message("merge.dialog.ignore.change.action.name"), null, AllIcons.Diff.Remove) {
        public void actionPerformed(AnActionEvent e) {
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

  public <T> T getUserData(@NotNull Key<T> key) {
    return myDataHolder.getUserData(key);
  }

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

}
