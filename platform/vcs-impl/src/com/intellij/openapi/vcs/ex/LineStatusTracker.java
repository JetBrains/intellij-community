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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author irengrig
 *         author: lesya
 */
public class LineStatusTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker");
  private static final Key<CanNotCalculateDiffPanel> PANEL_KEY =
    new Key<CanNotCalculateDiffPanel>("LineStatusTracker.CanNotCalculateDiffPanel");

  private final Object myLock = new Object();
  private BaseLoadState myBaseLoaded;

  private final Document myDocument;
  private final Document myUpToDateDocument;

  private List<Range> myRanges;

  private final Project myProject;

  private MyDocumentListener myDocumentListener;

  private boolean myBulkUpdate;
  private final Application myApplication;
  @Nullable private RevisionPack myBaseRevisionNumber;
  private String myPreviousBaseRevision;
  private boolean myAnathemaThrown;
  private FileEditorManager myFileEditorManager;
  private final VirtualFile myVirtualFile;
  private boolean myReleased = false;

  private LineStatusTracker(@NotNull final Document document,
                            @NotNull final Document upToDateDocument,
                            final Project project,
                            @Nullable final VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
    myApplication = ApplicationManager.getApplication();
    myDocument = document;
    myUpToDateDocument = upToDateDocument;
    myUpToDateDocument.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    myProject = project;
    myBaseLoaded = BaseLoadState.LOADING;
    synchronized (myLock) {
      myRanges = new ArrayList<Range>();
    }
    myAnathemaThrown = false;
    myFileEditorManager = FileEditorManager.getInstance(myProject);
  }

  public void initialize(@NotNull final String upToDateContent, @NotNull RevisionPack baseRevisionNumber) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (myLock) {
      try {
        if (myReleased) return;
        if (myBaseRevisionNumber != null && myBaseRevisionNumber.after(baseRevisionNumber)) return;

        myBaseRevisionNumber = baseRevisionNumber;
        myPreviousBaseRevision = null;

        myUpToDateDocument.setReadOnly(false);
        myUpToDateDocument.replaceString(0, myUpToDateDocument.getTextLength(), upToDateContent);
        myUpToDateDocument.setReadOnly(true);
        reinstallRanges();

        if (myDocumentListener == null) {
          myDocumentListener = new MyDocumentListener();
          myDocument.addDocumentListener(myDocumentListener);
        }
      }
      finally {
        myBaseLoaded = BaseLoadState.LOADED;
      }
    }
  }

  public void useCachedBaseRevision(final RevisionPack number) {
    synchronized (myLock) {
      assert myBaseRevisionNumber != null;
      if (myPreviousBaseRevision == null || myBaseRevisionNumber.after(number)) return;
      initialize(myPreviousBaseRevision, number);
    }
  }

  public boolean canUseBaseRevision(final RevisionPack number) {
    synchronized (myLock) {
      return myBaseRevisionNumber != null && myBaseRevisionNumber.equals(number) && myPreviousBaseRevision != null;
    }
  }

  private void reinstallRanges() {
    myApplication.assertReadAccessAllowed();

    synchronized (myLock) {
      removeAnathema();
      removeHighlightersFromMarkupModel();
      try {
        myRanges = new RangesBuilder(myDocument, myUpToDateDocument).getRanges();
      }
      catch (FilesTooBigForDiffException e) {
        myRanges.clear();
        installAnathema();
        return;
      }
      for (final Range range : myRanges) {
        range.setHighlighter(createHighlighter(range));
      }
    }
  }

  private void removeAnathema() {
    if (!myAnathemaThrown) return;
    myAnathemaThrown = false;
    final FileEditor[] editors = myFileEditorManager.getEditors(myVirtualFile);
    for (FileEditor editor : editors) {
      final CanNotCalculateDiffPanel panel = editor.getUserData(PANEL_KEY);
      if (panel != null) {
        myFileEditorManager.removeTopComponent(editor, panel);
        editor.putUserData(PANEL_KEY, null);
      }
    }
  }

  @SuppressWarnings({"AutoBoxing"})
  private RangeHighlighter createHighlighter(final Range range) {
    LOG.assertTrue(!myReleased, "Already released");

    int first =
      range.getOffset1() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getOffset1());

    int second =
      range.getOffset2() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getOffset2());

    final RangeHighlighter highlighter = DocumentMarkupModel.forDocument(myDocument, myProject, true)
      .addRangeHighlighter(first, second, HighlighterLayer.FIRST - 1, null, HighlighterTargetArea.LINES_IN_RANGE);

    final TextAttributes attr = LineStatusTrackerDrawing.getAttributesFor(range);
    highlighter.setErrorStripeMarkColor(attr.getErrorStripeColor());
    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
    highlighter.setLineMarkerRenderer(LineStatusTrackerDrawing.createRenderer(range, this));
    highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());

    final String tooltip;
    if (range.getOffset1() == range.getOffset2()) {
      if (range.getUOffset1() + 1 == range.getUOffset2()) {
        tooltip = VcsBundle.message("tooltip.text.line.before.deleted", range.getOffset1() + 1);
      }
      else {
        tooltip = VcsBundle.message("tooltip.text.lines.before.deleted", range.getOffset1() + 1, range.getUOffset2() - range.getUOffset1());
      }
    }
    else if (range.getOffset1() + 1 == range.getOffset2()) {
      tooltip = VcsBundle.message("tooltip.text.line.changed", range.getOffset1() + 1);
    }
    else {
      tooltip = VcsBundle.message("tooltip.text.lines.changed", range.getOffset1() + 1, range.getOffset2());
    }

    highlighter.setErrorStripeTooltip(tooltip);
    return highlighter;
  }

  public void release() {
    synchronized (myLock) {
      if (myDocumentListener != null) {
        myDocument.removeDocumentListener(myDocumentListener);
      }
      removeAnathema();
      removeHighlightersFromMarkupModel();
      myReleased = true;
    }
  }

  public Document getDocument() {
    return myDocument;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public List<Range> getRanges() {
    myApplication.assertReadAccessAllowed();

    synchronized (myLock) {
      return myRanges;
    }
  }

  public Document getUpToDateDocument() {
    myApplication.assertIsDispatchThread();
    return myUpToDateDocument;
  }

  public void startBulkUpdate() {
    synchronized (myLock) {
      if (myReleased) return;

      myBulkUpdate = true;
      removeAnathema();
      removeHighlightersFromMarkupModel();
    }
  }

  private void removeHighlightersFromMarkupModel() {
    synchronized (myLock) {
      for (Range range : myRanges) {
        if (range.getHighlighter() != null) {
          range.getHighlighter().dispose();
        }
        range.invalidate();
      }
      myRanges.clear();
    }
  }

  public void finishBulkUpdate() {
    synchronized (myLock) {
      if (myReleased) return;

      myBulkUpdate = false;
      reinstallRanges();
    }
  }

  /**
   * @return true if was cleared and base revision contents load should be started
   * false -> load was already started; after contents is loaded,
   */
  public void resetForBaseRevisionLoad() {
    myApplication.assertReadAccessAllowed();

    synchronized (myLock) {
      // there can be multiple resets before init -> take from document only firts time -> when right after install(),
      // where myPreviousBaseRevision become null
      if (BaseLoadState.LOADED.equals(myBaseLoaded) && myPreviousBaseRevision == null) {
        myPreviousBaseRevision = myUpToDateDocument.getText();
      }
      myUpToDateDocument.setReadOnly(false);
      myUpToDateDocument.setText("");
      myUpToDateDocument.setReadOnly(true);
      removeAnathema();
      removeHighlightersFromMarkupModel();
      myBaseLoaded = BaseLoadState.LOADING;
    }
  }

  private class MyDocumentListener extends DocumentAdapter {
    // We have 3 document versions:
    // * VCS version - upToDate*
    // * before change - my*
    // * after change - current*

    private int myFirstChangedLine;
    private int myLastChangedLine;
    private int myChangedLines;
    private int myTotalLines;
    private final VcsDirtyScopeManager myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      myApplication.assertWriteAccessAllowed();

      synchronized (myLock) {
        if (myReleased) return;
        if (myBulkUpdate || myAnathemaThrown || BaseLoadState.LOADED != myBaseLoaded) return;
        try {
          myFirstChangedLine = myDocument.getLineNumber(e.getOffset());
          myLastChangedLine = e.getOldLength() == 0 ? myFirstChangedLine : myDocument.getLineNumber(e.getOffset() + e.getOldLength() - 1);
          if (StringUtil.endsWithChar(e.getOldFragment(), '\n')) myLastChangedLine++;
          myChangedLines = myLastChangedLine - myFirstChangedLine;
          myTotalLines = getLineCount(e.getDocument());
        }
        catch (ProcessCanceledException ignore) {
        }
      }
    }

    @Override
    public void documentChanged(final DocumentEvent e) {
      myApplication.assertWriteAccessAllowed();

      synchronized (myLock) {
        if (myReleased) return;
        if (myBulkUpdate || myAnathemaThrown || BaseLoadState.LOADED != myBaseLoaded) return;
        try {
          int currentFirstChangedLine = myFirstChangedLine;
          int currentLastChangedLine =
            e.getNewLength() == 0 ? currentFirstChangedLine : myDocument.getLineNumber(e.getOffset() + e.getNewLength() - 1);
          if (StringUtil.endsWithChar(e.getNewFragment(), '\n')) currentLastChangedLine++;
          int currentChangedLines = currentLastChangedLine - currentFirstChangedLine;
          int upToDateTotalLine = getLineCount(myUpToDateDocument);

          int linesShift = currentChangedLines - myChangedLines;

          List<Range> rangesBeforeChange = new ArrayList<Range>();
          List<Range> rangesAfterChange = new ArrayList<Range>();
          List<Range> changedRanges = new ArrayList<Range>();
          sortRanges(myRanges, myFirstChangedLine, myLastChangedLine, rangesBeforeChange, changedRanges, rangesAfterChange);

          Range firstChangedRange = ContainerUtil.getFirstItem(changedRanges);
          Range lastChangedRange = ContainerUtil.getLastItem(changedRanges);
          Range lastRangeBefore = ContainerUtil.getLastItem(rangesBeforeChange);
          Range firstRangeAfter = ContainerUtil.getFirstItem(rangesAfterChange);

          if (firstChangedRange != null && firstChangedRange.getOffset1() < myFirstChangedLine) {
            myFirstChangedLine = firstChangedRange.getOffset1();
          }
          if (lastChangedRange != null && lastChangedRange.getOffset2() > myLastChangedLine) {
            myLastChangedLine = lastChangedRange.getOffset2() - 1;
          }

          currentFirstChangedLine = myFirstChangedLine;
          currentLastChangedLine = myLastChangedLine + linesShift;

          int upToDateFirstLine = getUpToDateLine1(lastRangeBefore, myFirstChangedLine);
          int upToDateLastLine = getUpToDateLine2(firstRangeAfter, myLastChangedLine, myTotalLines, upToDateTotalLine);

          List<Range> newChangedRanges =
            getNewChangedRanges(currentFirstChangedLine, currentLastChangedLine, upToDateFirstLine, upToDateLastLine);

          shiftRanges(rangesAfterChange, linesShift);

          if (!changedRanges.equals(newChangedRanges)) {
            replaceRanges(changedRanges, newChangedRanges);

            myRanges = new ArrayList<Range>(rangesBeforeChange.size() + newChangedRanges.size() + rangesAfterChange.size());

            myRanges.addAll(rangesBeforeChange);
            myRanges.addAll(newChangedRanges);
            myRanges.addAll(rangesAfterChange);

            for (Range range : myRanges) {
              if (!range.hasHighlighter()) range.setHighlighter(createHighlighter(range));
            }

            if (myRanges.isEmpty() && myVirtualFile != null) {
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  FileDocumentManager.getInstance().saveDocument(e.getDocument());
                  boolean[] stillEmpty = new boolean[1];
                  synchronized (myLock) {
                    stillEmpty[0] = myRanges.isEmpty();
                  }
                  if (stillEmpty[0]) {
                    // file was modified, and now it's not -> dirty local change
                    myVcsDirtyScopeManager.fileDirty(myVirtualFile);
                  }
                }
              });
            }
          }
        }
        catch (ProcessCanceledException ignore) {
        }
        catch (FilesTooBigForDiffException e1) {
          installAnathema();
          removeHighlightersFromMarkupModel();
        }
      }
    }

    private int getUpToDateLine1(@Nullable Range range, int line) {
      return range == null ? line : line + range.getUOffset2() - range.getOffset2();
    }

    private int getUpToDateLine2(@Nullable Range range, int line, int totalLinesBefore, int totalLinesAfter) {
      return range == null ? totalLinesAfter - totalLinesBefore + line : line + range.getUOffset1() - range.getOffset1();
    }

    private List<Range> getNewChangedRanges(int firstChangedLine, int lastChangedLine, int upToDateFirstLine, int upToDateLastLine)
      throws FilesTooBigForDiffException {
      List<String> lines = new DocumentWrapper(myDocument).getLines(firstChangedLine, lastChangedLine);
      List<String> uLines = new DocumentWrapper(myUpToDateDocument).getLines(upToDateFirstLine, upToDateLastLine);
      return new RangesBuilder(lines, uLines, firstChangedLine, upToDateFirstLine).getRanges();
    }

    private void replaceRanges(@NotNull List<Range> rangesInChange, @NotNull List<Range> newRangesInChange) {
      for (Range range : rangesInChange) {
        if (range.getHighlighter() != null) {
          range.getHighlighter().dispose();
        }
        range.setHighlighter(null);
        range.invalidate();
      }
      for (Range range : newRangesInChange) {
        range.setHighlighter(createHighlighter(range));
      }
    }

    private void shiftRanges(@NotNull List<Range> rangesAfterChange, int shift) {
      for (final Range aRangesAfterChange : rangesAfterChange) {
        aRangesAfterChange.shift(shift);
      }
    }
  }

  public static void sortRanges(@NotNull List<Range> ranges,
                                int firstChangedLine,
                                int lastChangedLine,
                                @NotNull List<Range> rangesBeforeChange,
                                @NotNull List<Range> changedRanges,
                                @NotNull List<Range> rangesAfterChange) {
    for (Range range : ranges) {
      int offset1 = range.getOffset1() - 1;
      int offset2 = range.getOffset2();

      if (offset2 < firstChangedLine) {
        rangesBeforeChange.add(range);
      }
      else if (offset1 > lastChangedLine) {
        rangesAfterChange.add(range);
      }
      else {
        changedRanges.add(range);
      }
    }
  }

  @Nullable
  Range getNextRange(final Range range) {
    synchronized (myLock) {
      final int index = myRanges.indexOf(range);
      if (index == myRanges.size() - 1) return null;
      return myRanges.get(index + 1);
    }
  }

  @Nullable
  Range getPrevRange(final Range range) {
    synchronized (myLock) {
      final int index = myRanges.indexOf(range);
      if (index <= 0) return null;
      return myRanges.get(index - 1);
    }
  }

  @Nullable
  public Range getNextRange(final int line) {
    synchronized (myLock) {
      final Range currentRange = getRangeForLine(line);
      if (currentRange != null) {
        return getNextRange(currentRange);
      }

      for (final Range range : myRanges) {
        if (line > range.getOffset1() || line > range.getOffset2()) {
          continue;
        }
        return range;
      }
      return null;
    }
  }

  @Nullable
  public Range getPrevRange(final int line) {
    synchronized (myLock) {
      final Range currentRange = getRangeForLine(line);
      if (currentRange != null) {
        return getPrevRange(currentRange);
      }

      for (ListIterator<Range> iterator = myRanges.listIterator(myRanges.size()); iterator.hasPrevious(); ) {
        final Range range = iterator.previous();
        if (range.getOffset1() > line) {
          continue;
        }
        return range;
      }
      return null;
    }
  }

  @Nullable
  public Range getRangeForLine(final int line) {
    synchronized (myLock) {
      for (final Range range : myRanges) {
        if (range.getType() == Range.DELETED && line == range.getOffset1()) {
          return range;
        }
        else if (line >= range.getOffset1() && line < range.getOffset2()) {
          return range;
        }
      }
      return null;
    }
  }

  public void rollbackChanges(@NotNull Range range) {
    myApplication.assertWriteAccessAllowed();

    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("Rollback of invalid range");
        return;
      }

      if (range.getType() == Range.MODIFIED) {
        TextRange currentTextRange = getCurrentTextRange(range);
        int offset1 = currentTextRange.getStartOffset();
        int offset2 = currentTextRange.getEndOffset();

        CharSequence upToDateContent = getUpToDateContent(range);
        myDocument.replaceString(offset1, offset2, upToDateContent);
      }
      else if (range.getType() == Range.INSERTED) {
        TextRange currentTextRange = getCurrentTextRange(range);
        int offset1 = currentTextRange.getStartOffset();
        int offset2 = currentTextRange.getEndOffset();

        if (offset1 > 0) {
          offset1--;
        }
        else if (offset2 < myDocument.getTextLength()) {
          offset2++;
        }
        myDocument.deleteString(offset1, offset2);
      }
      else if (range.getType() == Range.DELETED) {
        CharSequence content = getUpToDateContent(range);
        if (range.getOffset2() == getLineCount(myDocument)) {
          myDocument.insertString(myDocument.getTextLength(), "\n" + content);
        }
        else {
          myDocument.insertString(myDocument.getLineStartOffset(range.getOffset2()), content + "\n");
        }
      }
      else {
        throw new IllegalArgumentException("Unknown range type: " + range.getType());
      }
    }
  }

  public void rollbackChanges(@NotNull SegmentTree lines) {
    myApplication.assertWriteAccessAllowed();

    synchronized (myLock) {
      List<Range> affectedRanges = new ArrayList<Range>();

      boolean wasEnd = false;
      boolean simple = true;
      for (Range range : myRanges) {
        if (!range.isValid()) {
          LOG.warn("Rollback of invalid range");
          return;
        }

        boolean check;
        if (range.getOffset1() == range.getOffset2()) {
          check = lines.check(range.getOffset1());
        }
        else {
          check = lines.check(range.getOffset1(), range.getOffset2());
        }
        if (check) {
          if (wasEnd) simple = false;
          affectedRanges.add(range);
        }
        else {
          if (!affectedRanges.isEmpty()) wasEnd = true;
        }
      }

      if (simple) {
        rollbackChangesSimple(affectedRanges);
      }
      else {
        rollbackChangesComplex(affectedRanges);
      }
    }
  }

  private void rollbackChangesSimple(@NotNull List<Range> ranges) {
    if (ranges.isEmpty()) return;

    Range first = ranges.get(0);
    Range last = ranges.get(ranges.size() - 1);

    byte type = first == last ? first.getType() : Range.MODIFIED;
    final Range merged = new Range(first.getOffset1(), last.getOffset2(), first.getUOffset1(), last.getUOffset2(), type);

    // We don't expect complex Insertion/Deletion operation - they shouldn't exist
    assert type != Range.MODIFIED || (first.getOffset1() != last.getOffset2() && first.getUOffset1() != last.getUOffset2());

    rollbackChanges(merged);
  }

  private void rollbackChangesComplex(@NotNull List<Range> ranges) {
    // We can't relay on assumption, that revert of a single change will not affect any other.
    // This, among the others, is because of 'magic' ranges for revert, that will affect nearby lines implicitly.
    // So it's dangerous to apply ranges ony-by-one and we have to create single atomic modification.
    // Usage of Bulk mode will lead to full rebuild of tracker, and therefore will be slow..

    if (ranges.isEmpty()) return;
    if (ranges.size() == 1) {
      rollbackChanges(ranges.get(0));
      return;
    }

    Range first = ranges.get(0);
    Range last = ranges.get(ranges.size() - 1);

    // We don't expect complex Insertion/Deletion operation - they shouldn't exist.
    assert first != last && first.getOffset1() != last.getOffset2() && first.getUOffset1() != last.getUOffset2();

    final int start = getCurrentTextRange(first).getStartOffset();
    final int end = getCurrentTextRange(last).getEndOffset();

    StringBuilder builder = new StringBuilder();

    int lastOffset = start;
    for (Range range : ranges) {
      TextRange textRange = getCurrentTextRange(range);

      builder.append(myDocument.getText(new TextRange(lastOffset, textRange.getStartOffset())));
      lastOffset = textRange.getEndOffset();

      if (range.getType() == Range.MODIFIED) {
        builder.append(getUpToDateContent(range));
      }
      else if (range.getType() == Range.INSERTED) {
        if (builder.length() > 0) {
          builder.deleteCharAt(builder.length() - 1);
        }
        else {
          lastOffset++;
        }
      }
      else if (range.getType() == Range.DELETED) {
        CharSequence content = getUpToDateContent(range);
        if (range.getOffset2() == getLineCount(myDocument)) {
          builder.append('\n').append(content);
        }
        else {
          builder.append(content).append('\n');
        }
      }
      else {
        throw new IllegalArgumentException("Unknown range type: " + range.getType());
      }
    }
    builder.append(myDocument.getText(new TextRange(lastOffset, end)));

    final String s = builder.toString();

    myDocument.replaceString(start, end, s);
  }

  public CharSequence getUpToDateContent(@NotNull Range range) {
    synchronized (myLock) {
      TextRange textRange = getUpToDateRange(range);
      final int startOffset = textRange.getStartOffset();
      final int endOffset = textRange.getEndOffset();
      return myUpToDateDocument.getCharsSequence().subSequence(startOffset, endOffset);
    }
  }

  @NotNull
  TextRange getCurrentTextRange(@NotNull Range range) {
    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("Current TextRange of invalid range");
      }

      return getRange(range.getOffset1(), range.getOffset2(), myDocument);
    }
  }

  @NotNull
  TextRange getUpToDateRange(@NotNull Range range) {
    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("UpToDate TextRange of invalid range");
      }

      return getRange(range.getUOffset1(), range.getUOffset2(), myUpToDateDocument);
    }
  }

  /**
   * Return affected range, without non-internal '\n'
   * so if last line is not empty, the last symbol will be not '\n'
   * <p/>
   * So we consider '\n' not as a part of line, but a separator between lines
   */
  @NotNull
  private static TextRange getRange(int offset1, int offset2, @NotNull Document document) {
    if (offset1 == offset2) {
      int lineStartOffset = offset1 < getLineCount(document) ? document.getLineStartOffset(offset1) : document.getTextLength();
      return new TextRange(lineStartOffset, lineStartOffset);
    }
    else {
      int startOffset = document.getLineStartOffset(offset1);
      int endOffset = document.getLineEndOffset(offset2 - 1);
      return new TextRange(startOffset, endOffset);
    }
  }

  public static LineStatusTracker createOn(@Nullable VirtualFile virtualFile, @NotNull final Document doc, final Project project) {
    final Document document = new DocumentImpl("", true);
    return new LineStatusTracker(doc, document, project, virtualFile);
  }

  public void baseRevisionLoadFailed() {
    synchronized (myLock) {
      myBaseLoaded = BaseLoadState.FAILED;
    }
  }

  Project getProject() {
    return myProject;
  }

  public enum BaseLoadState {
    LOADING,
    FAILED,
    LOADED
  }

  public static class RevisionPack {
    private final long myNumber;
    private final VcsRevisionNumber myRevision;

    public RevisionPack(long number, VcsRevisionNumber revision) {
      myNumber = number;
      myRevision = revision;
    }

    public long getNumber() {
      return myNumber;
    }

    public VcsRevisionNumber getRevision() {
      return myRevision;
    }

    public boolean after(final RevisionPack previous) {
      if (myRevision.equals(previous.getRevision())) return false;
      return myNumber > previous.getNumber();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RevisionPack that = (RevisionPack)o;

      return myRevision.equals(that.getRevision());
    }

    @Override
    public int hashCode() {
      return myRevision.hashCode();
    }
  }

  private void installAnathema() {
    myAnathemaThrown = true;
    final FileEditor[] editors = myFileEditorManager.getAllEditors(myVirtualFile);
    for (FileEditor editor : editors) {
      CanNotCalculateDiffPanel panel = editor.getUserData(PANEL_KEY);
      if (panel == null) {
        final CanNotCalculateDiffPanel newPanel = new CanNotCalculateDiffPanel();
        editor.putUserData(PANEL_KEY, newPanel);
        myFileEditorManager.addTopComponent(editor, newPanel);
      }
    }
  }

  public static class CanNotCalculateDiffPanel extends EditorNotificationPanel {
    public CanNotCalculateDiffPanel() {
      myLabel.setText("Can not highlight changed lines. File is too big and there are too many changes.");
    }
  }

  private static int getLineCount(@NotNull Document document) {
    return Math.max(document.getLineCount(), 1);
  }
}
