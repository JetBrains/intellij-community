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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledWithWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getLineCount;

/**
 * @author irengrig
 *         author: lesya
 */
@SuppressWarnings("MethodMayBeStatic")
public class LineStatusTracker {
  public enum Mode {DEFAULT, SMART, SILENT}

  public static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker");
  private static final Key<CanNotCalculateDiffPanel> PANEL_KEY =
    new Key<CanNotCalculateDiffPanel>("LineStatusTracker.CanNotCalculateDiffPanel");

  private final Object myLock = new Object();

  @NotNull private final Project myProject;
  @NotNull private final Document myDocument;
  @NotNull private final Document myVcsDocument;
  @NotNull private final VirtualFile myVirtualFile;

  @NotNull private final Application myApplication;
  @NotNull private final FileEditorManager myFileEditorManager;
  @NotNull private final VcsDirtyScopeManager myVcsDirtyScopeManager;

  @NotNull private final MyDocumentListener myDocumentListener;
  @NotNull private final ApplicationAdapter myApplicationListener;

  @Nullable private RevisionPack myBaseRevisionNumber;

  private boolean myInitialized;
  private boolean myDuringRollback;
  private boolean myBulkUpdate;
  private boolean myAnathemaThrown;
  private boolean myReleased;

  @NotNull private Mode myMode;

  @NotNull private List<Range> myRanges;

  @Nullable private DirtyRange myDirtyRange;

  private LineStatusTracker(@NotNull final Document document,
                            @NotNull final Document vcsDocument,
                            @NotNull final Project project,
                            @NotNull final VirtualFile virtualFile,
                            @NotNull final Mode mode) {
    myDocument = document;
    myVcsDocument = vcsDocument;
    myProject = project;
    myVirtualFile = virtualFile;

    myApplication = ApplicationManager.getApplication();
    myFileEditorManager = FileEditorManager.getInstance(myProject);
    myVcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    myDocumentListener = new MyDocumentListener();
    myDocument.addDocumentListener(myDocumentListener);

    myApplicationListener = new MyApplicationListener();
    ApplicationManager.getApplication().addApplicationListener(myApplicationListener);

    myMode = mode;

    myRanges = new ArrayList<Range>();
  }

  @CalledInAwt
  public void initialize(@NotNull final String vcsContent, @NotNull RevisionPack baseRevisionNumber) {
    myApplication.assertIsDispatchThread();

    synchronized (myLock) {
      try {
        if (myInitialized || myReleased) return;
        if (myBaseRevisionNumber != null && myBaseRevisionNumber.contains(baseRevisionNumber)) return;

        myBaseRevisionNumber = baseRevisionNumber;

        myVcsDocument.setReadOnly(false);
        myVcsDocument.setText(vcsContent);
        myVcsDocument.setReadOnly(true);
      }
      finally {
        myInitialized = true;
      }

      reinstallRanges();
    }
  }

  @CalledInAwt
  private void reinstallRanges() {
    myApplication.assertIsDispatchThread();

    synchronized (myLock) {
      if (!myInitialized || myReleased || myBulkUpdate) return;

      destroyRanges();
      try {
        myRanges = RangesBuilder.createRanges(myDocument, myVcsDocument, myMode == Mode.SMART);
        for (final Range range : myRanges) {
          createHighlighter(range);
        }

        if (myRanges.isEmpty()) {
          markFileUnchanged();
        }
      }
      catch (FilesTooBigForDiffException e) {
        installAnathema();
      }
    }
  }

  @CalledInAwt
  private void destroyRanges() {
    removeAnathema();
    removeHighlightersFromMarkupModel();
    myDirtyRange = null;
  }

  @CalledInAwt
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

  @CalledInAwt
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

  @CalledInAwt
  public void setMode(@NotNull Mode mode) {
    synchronized (myLock) {
      if (myMode == mode) return;
      myMode = mode;
      reinstallRanges();
    }
  }

  @CalledInAwt
  private void disposeHighlighter(@NotNull Range range) {
    try {
      range.invalidate();
      RangeHighlighter highlighter = range.getHighlighter();
      if (highlighter != null) {
        range.setHighlighter(null);
        highlighter.dispose();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @CalledInAwt
  private void createHighlighter(@NotNull Range range) {
    myApplication.assertIsDispatchThread();

    LOG.assertTrue(!myReleased, "Already released");
    if (range.getHighlighter() != null) {
      LOG.error("Multiple highlighters registered for the same Range");
      return;
    }

    if (myMode == Mode.SILENT) return;

    int first =
      range.getLine1() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getLine1());

    int second =
      range.getLine2() >= getLineCount(myDocument) ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getLine2());

    final TextAttributes attr = LineStatusTrackerDrawing.getAttributesFor(range);
    final RangeHighlighter highlighter = DocumentMarkupModel.forDocument(myDocument, myProject, true)
      .addRangeHighlighter(first, second, HighlighterLayer.FIRST - 1, attr, HighlighterTargetArea.LINES_IN_RANGE);

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

    range.setHighlighter(highlighter);
  }

  public boolean isReleased() {
    synchronized (myLock) {
      return myReleased;
    }
  }

  public boolean isValid() {
    synchronized (myLock) {
      return myInitialized && !myReleased && !myAnathemaThrown && !myBulkUpdate && !myDuringRollback && myDirtyRange == null;
    }
  }

  public void release() {
    synchronized (myLock) {
      myReleased = true;
      myDocument.removeDocumentListener(myDocumentListener);
      ApplicationManager.getApplication().removeApplicationListener(myApplicationListener);

      if (myApplication.isDispatchThread()) {
        destroyRanges();
      }
      else {
        invalidateRanges();
        myApplication.invokeLater(new Runnable() {
          @Override
          public void run() {
            destroyRanges();
          }
        });
      }
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  public Document getVcsDocument() {
    return myVcsDocument;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @NotNull
  @CalledInAwt
  public Mode getMode() {
    return myMode;
  }

  @CalledInAwt
  public boolean isSilentMode() {
    return myMode == Mode.SILENT;
  }

  @NotNull
  public List<Range> getRanges() {
    synchronized (myLock) {
      return Collections.unmodifiableList(myRanges);
    }
  }

  @CalledInAwt
  public void startBulkUpdate() {
    synchronized (myLock) {
      if (myReleased) return;

      myBulkUpdate = true;
      destroyRanges();
    }
  }

  @CalledInAwt
  public void finishBulkUpdate() {
    synchronized (myLock) {
      if (myReleased) return;

      myBulkUpdate = false;
      reinstallRanges();
    }
  }

  @CalledInAwt
  private void removeHighlightersFromMarkupModel() {
    myApplication.assertIsDispatchThread();

    synchronized (myLock) {
      for (Range range : myRanges) {
        disposeHighlighter(range);
      }
      myRanges = Collections.emptyList();
    }
  }

  private void invalidateRanges() {
    synchronized (myLock) {
      for (Range range : myRanges) {
        range.invalidate();
      }
    }
  }

  private void markFileUnchanged() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
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

  private class MyApplicationListener extends ApplicationAdapter {
    @Override
    public void writeActionFinished(Object action) {
      synchronized (myLock) {
        if (!myInitialized || myReleased || myBulkUpdate || myDuringRollback || myAnathemaThrown) return;
        if (myDirtyRange != null) {
          try {
            doUpdateRanges(myDirtyRange.line1, myDirtyRange.line2, myDirtyRange.lineShift, myDirtyRange.beforeTotalLines);
            myDirtyRange = null;
          }
          catch (Exception e) {
            LOG.error(e);
            reinstallRanges();
          }
        }
      }
    }
  }

  private class MyDocumentListener extends DocumentAdapter {
    /*
     *   beforeWriteLock   beforeChange     Current
     *              |            |             |
     *              |            | line1       |
     * updatedLine1 +============+-------------+ newLine1
     *              |            |             |
     *      r.line1 +------------+ oldLine1    |
     *              |            |             |
     *              |     old    |             |
     *              |    dirty   |             |
     *              |            | oldLine2    |
     *      r.line2 +------------+         ----+ newLine2
     *              |            |        /    |
     * updatedLine2 +============+--------     |
     *                            line2
     */

    private int myLine1;
    private int myLine2;
    private int myBeforeTotalLines;

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      myApplication.assertIsDispatchThread();

      synchronized (myLock) {
        if (!myInitialized || myReleased) return;
        if (myBulkUpdate || myDuringRollback || myAnathemaThrown) return;
        assert myDocument == e.getDocument();

        myLine1 = myDocument.getLineNumber(e.getOffset());
        if (e.getOldLength() == 0) {
          myLine2 = myLine1 + 1;
        }
        else {
          myLine2 = myDocument.getLineNumber(e.getOffset() + e.getOldLength()) + 1;
        }

        myBeforeTotalLines = getLineCount(myDocument);
      }
    }

    @Override
    public void documentChanged(final DocumentEvent e) {
      myApplication.assertIsDispatchThread();

      synchronized (myLock) {
        if (!myInitialized || myReleased) return;
        if (myBulkUpdate || myDuringRollback || myAnathemaThrown) return;
        assert myDocument == e.getDocument();

        int newLine1 = myLine1;
        int newLine2;
        if (e.getNewLength() == 0) {
          newLine2 = newLine1 + 1;
        }
        else {
          newLine2 = myDocument.getLineNumber(e.getOffset() + e.getNewLength()) + 1;
        }

        int linesShift = (newLine2 - newLine1) - (myLine2 - myLine1);

        int[] fixed = fixRanges(e, myLine1, myLine2);
        int line1 = fixed[0];
        int line2 = fixed[1];

        if (myDirtyRange == null) {
          myDirtyRange = new DirtyRange(line1, line2, linesShift, myBeforeTotalLines);
        }
        else {
          int oldLine1 = myDirtyRange.line1;
          int oldLine2 = myDirtyRange.line2 + myDirtyRange.lineShift;

          int updatedLine1 = myDirtyRange.line1 - Math.max(oldLine1 - line1, 0);
          int updatedLine2 = myDirtyRange.line2 + Math.max(line2 - oldLine2, 0);

          myDirtyRange = new DirtyRange(updatedLine1, updatedLine2, linesShift + myDirtyRange.lineShift, myDirtyRange.beforeTotalLines);
        }
      }
    }
  }

  @NotNull
  private int[] fixRanges(@NotNull DocumentEvent e, int line1, int line2) {
    CharSequence document = myDocument.getCharsSequence();
    int offset = e.getOffset();

    if (e.getOldLength() == 0 && e.getNewLength() != 0) {
      if (StringUtil.endsWithChar(e.getNewFragment(), '\n') && isNewline(offset - 1, document)) {
        return new int[]{line1, line2 - 1};
      }
      if (StringUtil.startsWithChar(e.getNewFragment(), '\n') && isNewline(offset + e.getNewLength(), document)) {
        return new int[]{line1 + 1, line2};
      }
    }
    if (e.getOldLength() != 0 && e.getNewLength() == 0) {
      if (StringUtil.endsWithChar(e.getOldFragment(), '\n') && isNewline(offset - 1, document)) {
        return new int[]{line1, line2 - 1};
      }
      if (StringUtil.startsWithChar(e.getOldFragment(), '\n') && isNewline(offset + e.getNewLength(), document)) {
        return new int[]{line1 + 1, line2};
      }
    }

    return new int[]{line1, line2};
  }

  private static boolean isNewline(int offset, @NotNull CharSequence sequence) {
    if (offset < 0) return false;
    if (offset >= sequence.length()) return false;
    return sequence.charAt(offset) == '\n';
  }

  private void doUpdateRanges(int beforeChangedLine1,
                              int beforeChangedLine2,
                              int linesShift,
                              int beforeTotalLines) {
    List<Range> rangesBeforeChange = new ArrayList<Range>();
    List<Range> rangesAfterChange = new ArrayList<Range>();
    List<Range> changedRanges = new ArrayList<Range>();

    sortRanges(beforeChangedLine1, beforeChangedLine2, linesShift, rangesBeforeChange, changedRanges, rangesAfterChange);

    Range firstChangedRange = ContainerUtil.getFirstItem(changedRanges);
    Range lastChangedRange = ContainerUtil.getLastItem(changedRanges);

    if (firstChangedRange != null && firstChangedRange.getLine1() < beforeChangedLine1) {
      beforeChangedLine1 = firstChangedRange.getLine1();
    }
    if (lastChangedRange != null && lastChangedRange.getLine2() > beforeChangedLine2) {
      beforeChangedLine2 = lastChangedRange.getLine2();
    }

    doUpdateRanges(beforeChangedLine1, beforeChangedLine2, linesShift, beforeTotalLines,
                   rangesBeforeChange, changedRanges, rangesAfterChange);
  }

  private void doUpdateRanges(int beforeChangedLine1,
                              int beforeChangedLine2,
                              int linesShift, // before -> after
                              int beforeTotalLines,
                              @NotNull List<Range> rangesBefore,
                              @NotNull List<Range> changedRanges,
                              @NotNull List<Range> rangesAfter) {
    try {
      int vcsTotalLines = getLineCount(myVcsDocument);

      Range lastRangeBefore = ContainerUtil.getLastItem(rangesBefore);
      Range firstRangeAfter = ContainerUtil.getFirstItem(rangesAfter);

      //noinspection UnnecessaryLocalVariable
      int afterChangedLine1 = beforeChangedLine1;
      int afterChangedLine2 = beforeChangedLine2 + linesShift;

      int vcsLine1 = getVcsLine1(lastRangeBefore, beforeChangedLine1);
      int vcsLine2 = getVcsLine2(firstRangeAfter, beforeChangedLine2, beforeTotalLines, vcsTotalLines);

      List<Range> newChangedRanges = getNewChangedRanges(afterChangedLine1, afterChangedLine2, vcsLine1, vcsLine2);

      shiftRanges(rangesAfter, linesShift);

      if (!changedRanges.equals(newChangedRanges)) {
        myRanges = new ArrayList<Range>(rangesBefore.size() + newChangedRanges.size() + rangesAfter.size());

        myRanges.addAll(rangesBefore);
        myRanges.addAll(newChangedRanges);
        myRanges.addAll(rangesAfter);

        for (Range range : changedRanges) {
          disposeHighlighter(range);
        }
        for (Range range : newChangedRanges) {
          createHighlighter(range);
        }

        if (myRanges.isEmpty()) {
          markFileUnchanged();
        }
      }
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (FilesTooBigForDiffException e1) {
      destroyRanges();
      installAnathema();
    }
  }

  private static int getVcsLine1(@Nullable Range range, int line) {
    return range == null ? line : line + range.getVcsLine2() - range.getLine2();
  }

  private static int getVcsLine2(@Nullable Range range, int line, int totalLinesBefore, int totalLinesAfter) {
    return range == null ? totalLinesAfter - totalLinesBefore + line : line + range.getVcsLine1() - range.getLine1();
  }

  private List<Range> getNewChangedRanges(int changedLine1, int changedLine2, int vcsLine1, int vcsLine2)
    throws FilesTooBigForDiffException {

    if (changedLine1 == changedLine2 && vcsLine1 == vcsLine2) {
      return Collections.emptyList();
    }
    if (changedLine1 == changedLine2) {
      return Collections.singletonList(new Range(changedLine1, changedLine2, vcsLine1, vcsLine2));
    }
    if (vcsLine1 == vcsLine2) {
      return Collections.singletonList(new Range(changedLine1, changedLine2, vcsLine1, vcsLine2));
    }

    List<String> lines = DiffUtil.getLines(myDocument, changedLine1, changedLine2);
    List<String> vcsLines = DiffUtil.getLines(myVcsDocument, vcsLine1, vcsLine2);

    return RangesBuilder.createRanges(lines, vcsLines, changedLine1, vcsLine1, myMode == Mode.SMART);
  }

  private static void shiftRanges(@NotNull List<Range> rangesAfterChange, int shift) {
    for (final Range range : rangesAfterChange) {
      range.shift(shift);
    }
  }

  private void sortRanges(int beforeChangedLine1,
                          int beforeChangedLine2,
                          int linesShift,
                          @NotNull List<Range> rangesBeforeChange,
                          @NotNull List<Range> changedRanges,
                          @NotNull List<Range> rangesAfterChange) {
    int lastBefore = -1;
    int firstAfter = myRanges.size();
    for (int i = 0; i < myRanges.size(); i++) {
      Range range = myRanges.get(i);

      if (range.getLine2() < beforeChangedLine1) {
        lastBefore = i;
      }
      else if (range.getLine1() > beforeChangedLine2) {
        firstAfter = i;
        break;
      }
    }

    if (Registry.is("diff.status.tracker.skip.spaces")) {
      // Expand on ranges, that are separated from changed lines only by whitespaces

      while (lastBefore != -1) {
        int firstChangedLine = beforeChangedLine1;
        if (lastBefore + 1 < myRanges.size()) {
          Range firstChanged = myRanges.get(lastBefore + 1);
          firstChangedLine = Math.min(firstChangedLine, firstChanged.getLine1());
        }

        if (!isLineRangeEmpty(myDocument, myRanges.get(lastBefore).getLine2(), firstChangedLine)) {
          break;
        }

        lastBefore--;
      }

      while (firstAfter != myRanges.size()) {
        int firstUnchangedLineAfter = beforeChangedLine2 + linesShift;
        if (firstAfter > 0) {
          Range lastChanged = myRanges.get(firstAfter - 1);
          firstUnchangedLineAfter = Math.max(firstUnchangedLineAfter, lastChanged.getLine2() + linesShift);
        }

        if (!isLineRangeEmpty(myDocument, firstUnchangedLineAfter, myRanges.get(firstAfter).getLine1() + linesShift)) {
          break;
        }

        firstAfter++;
      }
    }

    for (int i = 0; i < myRanges.size(); i++) {
      Range range = myRanges.get(i);
      if (i <= lastBefore) {
        rangesBeforeChange.add(range);
      }
      else if (i >= firstAfter) {
        rangesAfterChange.add(range);
      }
      else {
        changedRanges.add(range);
      }
    }
  }

  private static boolean isLineRangeEmpty(@NotNull Document document, int line1, int line2) {
    int lineCount = getLineCount(document);
    int startOffset = line1 == lineCount ? document.getTextLength() : document.getLineStartOffset(line1);
    int endOffset = line2 == lineCount ? document.getTextLength() : document.getLineStartOffset(line2);

    CharSequence interval = document.getImmutableCharSequence().subSequence(startOffset, endOffset);
    return StringUtil.isEmptyOrSpaces(interval);
  }

  @Nullable
  public Range getNextRange(Range range) {
    synchronized (myLock) {
      final int index = myRanges.indexOf(range);
      if (index == myRanges.size() - 1) return null;
      return myRanges.get(index + 1);
    }
  }

  @Nullable
  public Range getPrevRange(Range range) {
    synchronized (myLock) {
      final int index = myRanges.indexOf(range);
      if (index <= 0) return null;
      return myRanges.get(index - 1);
    }
  }

  @Nullable
  public Range getNextRange(int line) {
    synchronized (myLock) {
      for (Range range : myRanges) {
        if (line < range.getLine2() && !range.isSelectedByLine(line)) {
          return range;
        }
      }
      return null;
    }
  }

  @Nullable
  public Range getPrevRange(int line) {
    synchronized (myLock) {
      for (int i = myRanges.size() - 1; i >= 0; i--) {
        Range range = myRanges.get(i);
        if (line > range.getLine1() && !range.isSelectedByLine(line)) {
          return range;
        }
      }
      return null;
    }
  }

  @Nullable
  public Range getRangeForLine(int line) {
    synchronized (myLock) {
      for (final Range range : myRanges) {
        if (range.isSelectedByLine(line)) return range;
      }
      return null;
    }
  }

  private void doRollbackRange(@NotNull Range range) {
    DiffUtil.applyModification(myDocument, range.getLine1(), range.getLine2(), myVcsDocument, range.getVcsLine1(), range.getVcsLine2());

    markLinesUnchanged(range.getLine1(), range.getLine1() + range.getVcsLine2() - range.getVcsLine1());
  }

  private void markLinesUnchanged(int startLine, int endLine) {
    if (myDocument.getTextLength() == 0) return; // empty document has no lines
    if (startLine == endLine) return;
    ((DocumentImpl)myDocument).clearLineModificationFlags(startLine, endLine);
  }

  @CalledWithWriteLock
  public void rollbackChanges(@NotNull Range range) {
    rollbackChanges(Collections.singletonList(range));
  }

  @CalledWithWriteLock
  public void rollbackChanges(@NotNull final BitSet lines) {
    List<Range> toRollback = new ArrayList<Range>();
    for (Range range : myRanges) {
      boolean check = DiffUtil.isSelectedByLine(lines, range.getLine1(), range.getLine2());
      if (check) {
        toRollback.add(range);
      }
    }

    rollbackChanges(toRollback);
  }

  /**
   * @param ranges - sorted list of ranges to rollback
   */
  @CalledWithWriteLock
  private void rollbackChanges(@NotNull final List<Range> ranges) {
    runBulkRollback(new Runnable() {
      @Override
      public void run() {
        Range first = null;
        Range last = null;

        int shift = 0;
        for (Range range : ranges) {
          if (!range.isValid()) {
            LOG.warn("Rollback of invalid range");
            break;
          }

          if (first == null) {
            first = range;
          }
          last = range;

          Range shiftedRange = new Range(range);
          shiftedRange.shift(shift);

          doRollbackRange(shiftedRange);

          shift += (range.getVcsLine2() - range.getVcsLine1()) - (range.getLine2() - range.getLine1());
        }

        if (first != null) {
          int beforeChangedLine1 = first.getLine1();
          int beforeChangedLine2 = last.getLine2();

          int beforeTotalLines = getLineCount(myDocument) - shift;

          doUpdateRanges(beforeChangedLine1, beforeChangedLine2, shift, beforeTotalLines);
        }
      }
    });
  }

  @CalledWithWriteLock
  public void rollbackAllChanges() {
    runBulkRollback(new Runnable() {
      @Override
      public void run() {
        myDocument.setText(myVcsDocument.getText());

        destroyRanges();

        markFileUnchanged();
      }
    });
  }

  @CalledWithWriteLock
  private void runBulkRollback(@NotNull Runnable task) {
    myApplication.assertWriteAccessAllowed();

    synchronized (myLock) {
      if (!isValid()) return;

      try {
        myDuringRollback = true;

        task.run();
      }
      catch (Error e) {
        reinstallRanges();
        throw e;
      }
      catch (RuntimeException e) {
        reinstallRanges();
        throw e;
      }
      finally {
        myDuringRollback = false;
      }
    }
  }

  @NotNull
  public CharSequence getCurrentContent(@NotNull Range range) {
    synchronized (myLock) {
      TextRange textRange = getCurrentTextRange(range);
      final int startOffset = textRange.getStartOffset();
      final int endOffset = textRange.getEndOffset();
      return myDocument.getImmutableCharSequence().subSequence(startOffset, endOffset);
    }
  }

  @NotNull
  public CharSequence getVcsContent(@NotNull Range range) {
    synchronized (myLock) {
      TextRange textRange = getVcsTextRange(range);
      final int startOffset = textRange.getStartOffset();
      final int endOffset = textRange.getEndOffset();
      return myVcsDocument.getImmutableCharSequence().subSequence(startOffset, endOffset);
    }
  }

  @NotNull
  public TextRange getCurrentTextRange(@NotNull Range range) {
    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("Current TextRange of invalid range");
      }
      return DiffUtil.getLinesRange(myDocument, range.getLine1(), range.getLine2());
    }
  }

  @NotNull
  public TextRange getVcsTextRange(@NotNull Range range) {
    synchronized (myLock) {
      if (!range.isValid()) {
        LOG.warn("Vcs TextRange of invalid range");
      }
      return DiffUtil.getLinesRange(myVcsDocument, range.getVcsLine1(), range.getVcsLine2());
    }
  }

  public static LineStatusTracker createOn(@NotNull VirtualFile virtualFile, @NotNull final Document document, final Project project,
                                           @NotNull Mode mode) {
    final Document vcsDocument = new DocumentImpl("", true);
    vcsDocument.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
    return new LineStatusTracker(document, vcsDocument, project, virtualFile, mode);
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

    public boolean contains(final RevisionPack previous) {
      if (myRevision.equals(previous.getRevision()) && !myRevision.equals(VcsRevisionNumber.NULL)) return true;
      return myNumber >= previous.getNumber();
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

  private static class DirtyRange {
    public final int line1;
    public final int line2;
    public final int lineShift;
    public final int beforeTotalLines;

    public DirtyRange(int line1, int line2, int lineShift, int beforeTotalLines) {
      this.line1 = line1;
      this.line2 = line2;
      this.lineShift = lineShift;
      this.beforeTotalLines = beforeTotalLines;
    }
  }

  private static class CanNotCalculateDiffPanel extends EditorNotificationPanel {
    public CanNotCalculateDiffPanel() {
      myLabel.setText("Can not highlight changed lines. File is too big and there are too many changes.");
    }
  }
}
