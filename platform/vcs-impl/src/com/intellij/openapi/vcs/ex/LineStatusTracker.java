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
import java.util.BitSet;
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
  private final Document myVcsDocument;

  private List<Range> myRanges;

  private final Project myProject;

  private MyDocumentListener myDocumentListener;

  private boolean mySuppressUpdate;
  private boolean myBulkUpdate;
  private final Application myApplication;
  @Nullable private RevisionPack myBaseRevisionNumber;
  private String myPreviousBaseRevision;
  private boolean myAnathemaThrown;
  private FileEditorManager myFileEditorManager;
  private final VcsDirtyScopeManager myVcsDirtyScopeManager;
  private final VirtualFile myVirtualFile;
  private boolean myReleased = false;

  private LineStatusTracker(@NotNull final Document document,
                            @NotNull final Document vcsDocument,
                            final Project project,
                            @Nullable final VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
    myApplication = ApplicationManager.getApplication();
    myDocument = document;
    myVcsDocument = vcsDocument;
    myVcsDocument.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    myProject = project;
    myBaseLoaded = BaseLoadState.LOADING;
    myRanges = new ArrayList<Range>();
    myAnathemaThrown = false;
    myFileEditorManager = FileEditorManager.getInstance(myProject);
    myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  public void initialize(@NotNull final String vcsContent, @NotNull RevisionPack baseRevisionNumber) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (myLock) {
      try {
        if (myReleased) return;
        if (myBaseRevisionNumber != null && myBaseRevisionNumber.after(baseRevisionNumber)) return;

        myBaseRevisionNumber = baseRevisionNumber;
        myPreviousBaseRevision = null;

        myVcsDocument.setReadOnly(false);
        myVcsDocument.replaceString(0, myVcsDocument.getTextLength(), vcsContent);
        myVcsDocument.setReadOnly(true);
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
        myRanges = new RangesBuilder(myDocument, myVcsDocument).getRanges();
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
      range.getLine1() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getLine1());

    int second =
      range.getLine2() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getLine2());

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
    if (range.getLine1() == range.getLine2()) {
      if (range.getVcsLine1() + 1 == range.getVcsLine2()) {
        tooltip = VcsBundle.message("tooltip.text.line.before.deleted", range.getLine1() + 1);
      }
      else {
        tooltip = VcsBundle.message("tooltip.text.lines.before.deleted", range.getLine1() + 1, range.getVcsLine2() - range.getVcsLine1());
      }
    }
    else if (range.getLine1() + 1 == range.getLine2()) {
      tooltip = VcsBundle.message("tooltip.text.line.changed", range.getLine1() + 1);
    }
    else {
      tooltip = VcsBundle.message("tooltip.text.lines.changed", range.getLine1() + 1, range.getLine2());
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

  public Document getVcsDocument() {
    myApplication.assertIsDispatchThread();
    return myVcsDocument;
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
        myPreviousBaseRevision = myVcsDocument.getText();
      }
      myVcsDocument.setReadOnly(false);
      myVcsDocument.setText("");
      myVcsDocument.setReadOnly(true);
      removeAnathema();
      removeHighlightersFromMarkupModel();
      myBaseLoaded = BaseLoadState.LOADING;
    }
  }

  private class MyDocumentListener extends DocumentAdapter {
    // We have 3 document versions:
    // * VCS version
    // * before change
    // * after change

    private int myBeforeFirstChangedLine;
    private int myBeforeLastChangedLine;
    private int myBeforeTotalLines;

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      myApplication.assertWriteAccessAllowed();

      synchronized (myLock) {
        if (myReleased) return;
        if (myBulkUpdate || mySuppressUpdate || myAnathemaThrown || BaseLoadState.LOADED != myBaseLoaded) return;
        assert myDocument == e.getDocument();

        try {
          myBeforeFirstChangedLine = myDocument.getLineNumber(e.getOffset());
          myBeforeLastChangedLine = e.getOldLength() == 0 ? myBeforeFirstChangedLine : myDocument.getLineNumber(e.getOffset() + e.getOldLength() - 1);
          if (StringUtil.endsWithChar(e.getOldFragment(), '\n')) myBeforeLastChangedLine++;
          myBeforeTotalLines = getLineCount(myDocument);
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
        if (myBulkUpdate || mySuppressUpdate || myAnathemaThrown || BaseLoadState.LOADED != myBaseLoaded) return;
        assert myDocument == e.getDocument();

        int afterFirstChangedLine = myBeforeFirstChangedLine;
        int afterLastChangedLine =
          e.getNewLength() == 0 ? afterFirstChangedLine : myDocument.getLineNumber(e.getOffset() + e.getNewLength() - 1);
        if (StringUtil.endsWithChar(e.getNewFragment(), '\n')) afterLastChangedLine++;


        doUpdateRanges(myBeforeFirstChangedLine, myBeforeLastChangedLine, myBeforeTotalLines,
                       afterFirstChangedLine, afterLastChangedLine);
      }
    }
  }

  private void doUpdateRanges(int beforeFirstChangedLine,
                              int beforeLastChangedLine,
                              int beforeTotalLines,
                              int afterFirstChangedLine,
                              int afterLastChangedLine) {
    try {
      int vcsTotalLines = getLineCount(myVcsDocument);

      int afterChangedLines = afterLastChangedLine - afterFirstChangedLine;

      int beforeChangedLines = beforeLastChangedLine - beforeFirstChangedLine;
      int linesShift = afterChangedLines - beforeChangedLines;

      List<Range> rangesBeforeChange = new ArrayList<Range>();
      List<Range> rangesAfterChange = new ArrayList<Range>();
      List<Range> changedRanges = new ArrayList<Range>();
      sortRanges(myRanges, beforeFirstChangedLine, beforeLastChangedLine, rangesBeforeChange, changedRanges, rangesAfterChange);

      Range firstChangedRange = ContainerUtil.getFirstItem(changedRanges);
      Range lastChangedRange = ContainerUtil.getLastItem(changedRanges);
      Range lastRangeBefore = ContainerUtil.getLastItem(rangesBeforeChange);
      Range firstRangeAfter = ContainerUtil.getFirstItem(rangesAfterChange);

      if (firstChangedRange != null && firstChangedRange.getLine1() < beforeFirstChangedLine) {
        beforeFirstChangedLine = firstChangedRange.getLine1();
      }
      if (lastChangedRange != null && lastChangedRange.getLine2() > beforeLastChangedLine) {
        beforeLastChangedLine = lastChangedRange.getLine2() - 1;
      }

      afterFirstChangedLine = beforeFirstChangedLine;
      afterLastChangedLine = beforeLastChangedLine + linesShift;

      int vcsFirstLine = getVcsLine1(lastRangeBefore, beforeFirstChangedLine);
      int vcsLastLine = getVcsLine2(firstRangeAfter, beforeLastChangedLine, beforeTotalLines, vcsTotalLines);

      List<Range> newChangedRanges = getNewChangedRanges(afterFirstChangedLine, afterLastChangedLine, vcsFirstLine, vcsLastLine);

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
              FileDocumentManager.getInstance().saveDocument(myDocument);
              boolean stillEmpty;
              synchronized (myLock) {
                stillEmpty = myRanges.isEmpty();
              }
              if (stillEmpty) {
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

  private static int getVcsLine1(@Nullable Range range, int line) {
    return range == null ? line : line + range.getVcsLine2() - range.getLine2();
  }

  private static int getVcsLine2(@Nullable Range range, int line, int totalLinesBefore, int totalLinesAfter) {
    return range == null ? totalLinesAfter - totalLinesBefore + line : line + range.getVcsLine1() - range.getLine1();
  }

  private List<Range> getNewChangedRanges(int firstChangedLine, int lastChangedLine, int vcsFirstLine, int vcsLastLine)
    throws FilesTooBigForDiffException {
    List<String> lines = new DocumentWrapper(myDocument).getLines(firstChangedLine, lastChangedLine);
    List<String> uLines = new DocumentWrapper(myVcsDocument).getLines(vcsFirstLine, vcsLastLine);
    return new RangesBuilder(lines, uLines, firstChangedLine, vcsFirstLine).getRanges();
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

  private static void shiftRanges(@NotNull List<Range> rangesAfterChange, int shift) {
    for (final Range range : rangesAfterChange) {
      range.shift(shift);
    }
  }

  public static void sortRanges(@NotNull List<Range> ranges,
                                int firstChangedLine,
                                int lastChangedLine,
                                @NotNull List<Range> rangesBeforeChange,
                                @NotNull List<Range> changedRanges,
                                @NotNull List<Range> rangesAfterChange) {
    for (Range range : ranges) {
      int line1 = range.getLine1() - 1;
      int line2 = range.getLine2();

      if (line2 < firstChangedLine) {
        rangesBeforeChange.add(range);
      }
      else if (line1 > lastChangedLine) {
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
        if (line > range.getLine1() || line > range.getLine2()) {
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
        if (range.getLine1() > line) {
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
        if (range.getType() == Range.DELETED && line == range.getLine1()) {
          return range;
        }
        else if (line >= range.getLine1() && line < range.getLine2()) {
          return range;
        }
      }
      return null;
    }
  }

  private void doRollbackRange(@NotNull Range range) {
    if (range.getType() == Range.MODIFIED) {
      TextRange currentTextRange = getCurrentTextRange(range);
      int offset1 = currentTextRange.getStartOffset();
      int offset2 = currentTextRange.getEndOffset();

      CharSequence vcsContent = getVcsContent(range);
      myDocument.replaceString(offset1, offset2, vcsContent);
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
      CharSequence content = getVcsContent(range);
      if (range.getLine2() == getLineCount(myDocument)) {
        myDocument.insertString(myDocument.getTextLength(), "\n" + content);
      }
      else {
        myDocument.insertString(myDocument.getLineStartOffset(range.getLine2()), content + "\n");
      }
    }
    else {
      throw new IllegalArgumentException("Unknown range type: " + range.getType());
    }
  }

  public void rollbackChanges(@NotNull Range range) {
    myApplication.assertWriteAccessAllowed();

    synchronized (myLock) {
      if (myBulkUpdate) return;

      if (!range.isValid()) {
        LOG.warn("Rollback of invalid range");
        return;
      }

      doRollbackRange(range);
    }
  }

  public void rollbackChanges(@NotNull BitSet lines) {
    myApplication.assertWriteAccessAllowed();

    synchronized (myLock) {
      if (myBulkUpdate) return;

      mySuppressUpdate = true;

      Range first = null;
      Range last = null;

      int shift = 0;
      for (Range range : myRanges) {
        if (!range.isValid()) {
          LOG.warn("Rollback of invalid range");
          break;
        }

        boolean check;
        if (range.getLine1() == range.getLine2()) {
          check = lines.get(range.getLine1());
        }
        else {
          check = checkRange(lines, range.getLine1(), range.getLine2());
        }

        if (check) {
          if (first == null) {
            first = range;
          }
          last = range;

          Range shiftedRange = new Range(range);
          shiftedRange.shift(shift);
          doRollbackRange(shiftedRange);
          shift += (range.getVcsLine2() - range.getVcsLine1()) - (range.getLine2() - range.getLine1());
        }
      }

      if (first != null) {
        int beforeFirstChangedLine = first.getLine1();
        int beforeLastChangedLine = first.getLine1() == last.getLine2() ? beforeFirstChangedLine : last.getLine2() - 1;
        int afterFirstChangedLine = beforeFirstChangedLine;
        int afterLastChangedLine = beforeLastChangedLine + shift;

        int beforeTotalLines = getLineCount(myDocument) - shift;

        doUpdateRanges(beforeFirstChangedLine, beforeLastChangedLine, beforeTotalLines,
                       afterFirstChangedLine, afterLastChangedLine);
      }

      mySuppressUpdate = false;
    }
  }

  private static boolean checkRange(@NotNull BitSet lines, int start, int end) {
    int next = lines.nextSetBit(start);
    if (next == -1) return false;
    return next < end;
  }

  public CharSequence getVcsContent(@NotNull Range range) {
    synchronized (myLock) {
      TextRange textRange = getVcsRange(range);
      final int startOffset = textRange.getStartOffset();
      final int endOffset = textRange.getEndOffset();
      return myVcsDocument.getCharsSequence().subSequence(startOffset, endOffset);
    }
  }

  @NotNull
  TextRange getCurrentTextRange(@NotNull Range range) {
    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("Current TextRange of invalid range");
      }

      return getRange(range.getLine1(), range.getLine2(), myDocument);
    }
  }

  @NotNull
  TextRange getVcsRange(@NotNull Range range) {
    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("Vcs TextRange of invalid range");
      }

      return getRange(range.getVcsLine1(), range.getVcsLine2(), myVcsDocument);
    }
  }

  /**
   * Return affected range, without non-internal '\n'
   * so if last line is not empty, the last symbol will be not '\n'
   * <p/>
   * So we consider '\n' not as a part of line, but a separator between lines
   */
  @NotNull
  private static TextRange getRange(int line1, int line2, @NotNull Document document) {
    if (line1 == line2) {
      int lineStartOffset = line1 < getLineCount(document) ? document.getLineStartOffset(line1) : document.getTextLength();
      return new TextRange(lineStartOffset, lineStartOffset);
    }
    else {
      int startOffset = document.getLineStartOffset(line1);
      int endOffset = document.getLineEndOffset(line2 - 1);
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
