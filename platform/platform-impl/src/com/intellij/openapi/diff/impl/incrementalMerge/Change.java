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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Represents a change in diff or merge view.
 * A change has two {@link com.intellij.openapi.diff.impl.incrementalMerge.Change.SimpleChangeSide sides} (left and right), each of them representing the text which has been changed and the original text
 * shown in the diff/merge.
 * Change can be applied, then its sides would be equal.
 */
public abstract class Change {
  private static final Logger LOG = Logger.getInstance(Change.class);

  public abstract ChangeSide getChangeSide(FragmentSide side);

  public abstract ChangeType getType();

  public abstract ChangeList getChangeList();

  protected abstract void removeFromList();

  /**
   * Called when a change has been applied.
   */
  public abstract void onApplied();

  /**
   * Called when a change has been removed from the list.
   */
  public abstract void onRemovedFromList();

  public abstract boolean isValid();

  /**
   * Apply the change, i.e. change the "Merge result" document and update range markers, highlighting, gutters, etc.
   * @param original The source side of the change, which is being applied.
   */
  private void apply(@NotNull FragmentSide original) {
    FragmentSide targetSide = original.otherSide();
    RangeMarker originalRangeMarker = getRangeMarker(original);
    RangeMarker rangeMarker = getRangeMarker(targetSide);

    TextRange textRange = modifyDocument(getProject(), originalRangeMarker, rangeMarker);
    if (textRange != null && isValid()) {
      updateTargetRangeMarker(targetSide, textRange);
    }
    onApplied();
  }

  /**
   * Updates the target marker of a change after the change has been applied
   * to allow highlighting of the document modification which has been performed.
   * @param targetFragmentSide The side to be changed.
   * @param updatedTextRange   New text range to be applied to the side.
   */
  protected final void updateTargetRangeMarker(@NotNull FragmentSide targetFragmentSide, @NotNull TextRange updatedTextRange) {
    ChangeSide targetSide = getChangeSide(targetFragmentSide);
    DiffRangeMarker originalRange = targetSide.getRange();
    DiffRangeMarker updatedRange = new DiffRangeMarker(originalRange.getDocument(), updatedTextRange, null);
    changeSide(targetSide, updatedRange);
  }

  /**
   * Substitutes the specified side of this change to a new side which contains the given range.
   * @param sideToChange The side to be changed.
   * @param newRange     New text range of the new side.
   */
  protected abstract void changeSide(ChangeSide sideToChange, DiffRangeMarker newRange);

  /**
   * Applies the text from the original marker to the target marker.
   * @return the resulting TextRange from the target document, or null if the document if not writable.
   */
  @Nullable
  private static TextRange modifyDocument(@Nullable Project project, @NotNull RangeMarker original, @NotNull RangeMarker target) {
    Document document = target.getDocument();
    if (project != null && !ReadonlyStatusHandler.ensureDocumentWritable(project, document)) {
      return null;
    }
    if (DocumentUtil.isEmpty(original)) {
      int offset = target.getStartOffset();
      document.deleteString(offset, target.getEndOffset());
    }
    CharSequence text = original.getDocument().getImmutableCharSequence().subSequence(original.getStartOffset(), original.getEndOffset());
    int startOffset = target.getStartOffset();
    if (DocumentUtil.isEmpty(target)) {
      document.insertString(startOffset, text);
    } else {
      document.replaceString(startOffset, target.getEndOffset(), text);
    }
    return new TextRange(startOffset, startOffset + text.length());
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

  @Nullable
  private Project getProject() {
    return getChangeList().getProject();
  }

  @NotNull
  private ChangeHighlighterHolder getHighlighterHolder(FragmentSide side) {
    return getChangeSide(side).getHighlighterHolder();
  }

  @NotNull
  private RangeMarker getRangeMarker(FragmentSide side) {
    ChangeSide changeSide = getChangeSide(side);
    LOG.assertTrue(changeSide != null);
    return changeSide.getRange();
  }

  public static void apply(final Change change, final FragmentSide fromSide) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(change.getProject(), new Runnable() {
          @Override
          public void run() {
            doApply(change, fromSide);
          }
        }, null, DiffBundle.message("save.merge.result.command.name"));
      }
    });
  }

  public static void doApply(final Change change, final FragmentSide fromSide) {
    change.apply(fromSide);
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

    @Override
    public int compare(@NotNull Change change, @NotNull Change change1) {
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
    private final ChangeHighlighterHolder myHighlighterHolder;

    public SimpleChangeSide(FragmentSide side, DiffRangeMarker rangeMarker) {
      mySide = side;
      myRange = rangeMarker;
      myHighlighterHolder = new ChangeHighlighterHolder();
    }

    public SimpleChangeSide(@NotNull ChangeSide originalSide, @NotNull DiffRangeMarker newRange) {
      mySide = ((SimpleChangeSide)originalSide).getFragmentSide();
      myRange = newRange;
      myHighlighterHolder = originalSide.getHighlighterHolder();
    }

    @NotNull
    public FragmentSide getFragmentSide() {
      return mySide;
    }

    @Override
    @NotNull
    public DiffRangeMarker getRange() {
      return myRange;
    }

    @NotNull
    @Override
    public ChangeHighlighterHolder getHighlighterHolder() {
      return myHighlighterHolder;
    }
  }
}
