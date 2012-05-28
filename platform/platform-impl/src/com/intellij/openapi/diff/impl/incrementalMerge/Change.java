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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.DocumentUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Represents a change in diff or merge view.
 * A change has two {@link com.intellij.openapi.diff.impl.incrementalMerge.Change.SimpleChangeSide sides} (left and right), each of them representing the text which has been changed and the original text
 * shown in the diff/merge.
 * Change can be applied, then its sides would be equal.
 */
public abstract class Change {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.Change");

  public abstract ChangeSide getChangeSide(FragmentSide side);

  public abstract ChangeType getType();

  public abstract ChangeList getChangeList();

  protected abstract void removeFromList();

  public abstract void onRemovedFromList();

  public abstract boolean isValid();

  private void apply(@NotNull FragmentSide original) {
    FragmentSide targetSide = original.otherSide();
    RangeMarker originalRangeMarker = getRangeMarker(original);
    RangeMarker rangeMarker = getRangeMarker(targetSide);

    if (originalRangeMarker != null && rangeMarker != null) {
      apply(getProject(), originalRangeMarker, rangeMarker);
      if (isValid()) {
        removeFromList();
      }
    }
  }

  private static void apply(@NotNull Project project, @NotNull RangeMarker original, @NotNull RangeMarker target) {
    Document document = target.getDocument();
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, document)) return;
    if (DocumentUtil.isEmpty(original)) {
      int offset = target.getStartOffset();
      document.deleteString(offset, target.getEndOffset());
    }
    String text = DocumentUtil.getText(original);
    int startOffset = target.getStartOffset();
    if (DocumentUtil.isEmpty(target)) {
      document.insertString(startOffset, text);
    } else {
      document.replaceString(startOffset, target.getEndOffset(), text);
    }
  }

  public void addMarkup(Editor[] editors) {
    LOG.assertTrue(editors.length == 2);
    highlight(editors, FragmentSide.SIDE1);
    highlight(editors, FragmentSide.SIDE2);
  }

  private void highlight(Editor[] editors, FragmentSide side) {
    getHighlighterHolder(side).highlight(getChangeSide(side), editors[side.getIndex()], getType());
  }

  private void updateHighlighter(FragmentSide side) {
    getHighlighterHolder(side).updateHighlighter(getChangeSide(side), getType());
  }

  private Project getProject() { return getChangeList().getProject(); }

  private ChangeHighlighterHolder getHighlighterHolder(FragmentSide side) {
    return getChangeSide(side).getHighlighterHolder();
  }

  private RangeMarker getRangeMarker(FragmentSide side) {
    ChangeSide changeSide = getChangeSide(side);
    LOG.assertTrue(changeSide != null);
    return changeSide.getRange();
  }

  public static void apply(final Change change, final FragmentSide fromSide) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(change.getProject(), new Runnable() {
          public void run() {
            change.apply(fromSide);
          }
        }, null, DiffBundle.message("save.merge.result.command.name"));
      }
    });
  }

  public void updateMarkup() {
    updateHighlighter(FragmentSide.SIDE1);
    updateHighlighter(FragmentSide.SIDE2);
  }

  public boolean canHasActions(FragmentSide fromSide) {
    FragmentSide targetSide = fromSide.otherSide();
    Document targetDocument = getChangeList().getDocument(targetSide);
    if (!targetDocument.isWritable()) return false;
    Editor targetEditor = getHighlighterHolder(targetSide).getEditor();
    return !targetEditor.isViewer();
  }

  public static class ChangeOrder implements Comparator<Change> {
    private final FragmentSide myMainSide;

    public ChangeOrder(FragmentSide mainSide) {
      myMainSide = mainSide;
    }

    public int compare(Change change, Change change1) {
      int result1 = compareSide(change, change1, myMainSide);
      if (result1 != 0) return result1;
      return compareSide(change, change1, myMainSide.otherSide());
    }

    private static int compareSide(Change change, Change change1, FragmentSide side) {
      return RangeMarker.BY_START_OFFSET.compare(change.getRangeMarker(side), change1.getRangeMarker(side));
    }
  }

  protected static class SimpleChangeSide extends ChangeSide {
    private final FragmentSide mySide;
    private final DiffRangeMarker myRange;
    private final ChangeHighlighterHolder myHighlighterHolder = new ChangeHighlighterHolder();

    public SimpleChangeSide(FragmentSide side, DiffRangeMarker rangeMarker) {
      mySide = side;
      myRange = rangeMarker;
    }

    public FragmentSide getFragmentSide() {
      return mySide;
    }

    public DiffRangeMarker getRange() {
      return myRange;
    }

    public ChangeHighlighterHolder getHighlighterHolder() {
      return myHighlighterHolder;
    }
  }
}
