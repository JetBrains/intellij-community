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

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.ShowNextChangeMarkerAction;
import com.intellij.openapi.vcs.actions.ShowPrevChangeMarkerAction;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SideBorder2;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * author: lesya
 */
public class LineStatusTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker");
  private final Document myDocument;
  private final Document myUpToDateDocument;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private List<Range> myRanges = new ArrayList<Range>();
  private final Project myProject;
  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"}) private int myHighlighterCount = 0;

  private MyDocumentListener myDocumentListener;

  private boolean myIsReleased = false;
  private boolean myIsInitialized = false;
  private boolean myBulkUpdate;

  public LineStatusTracker(Document document, Document upToDateDocument, Project project) {
    myDocument = document;
    myUpToDateDocument = upToDateDocument;
    myUpToDateDocument.putUserData(UndoManager.DONT_RECORD_UNDO, Boolean.TRUE);
    myProject = project;
  }

  public synchronized void initialize(@NotNull final String upToDateContent) {
    if (myIsReleased) return;
    LOG.assertTrue(!myIsInitialized);
    try {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          myUpToDateDocument.replaceString(0, myUpToDateDocument.getTextLength(), StringUtil.convertLineSeparators(upToDateContent));
        }
      });

      myUpToDateDocument.setReadOnly(true);
      reinstallRanges();

      myDocumentListener = new MyDocumentListener();
      myDocument.addDocumentListener(myDocumentListener);
    }
    finally {
      myIsInitialized = true;
    }
  }

  private synchronized void reinstallRanges() {
    reinstallRanges(new RangesBuilder(myDocument, myUpToDateDocument).getRanges());
  }

  private void reinstallRanges(List<Range> ranges) {
    removeHighlighters();
    myRanges = ranges;
    addHighlighters();
  }

  private void addHighlighters() {
    for (Range range : myRanges) {
      if (!range.hasHighlighter()) range.setHighlighter(createHighlighter(range));
    }
  }

  @SuppressWarnings({"AutoBoxing"})
  synchronized private RangeHighlighter createHighlighter(Range range) {
    int first =
      range.getOffset1() >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getOffset1());

    int second =
      range.getOffset2() >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getOffset2());


    RangeHighlighter highlighter = myDocument.getMarkupModel(myProject)
      .addRangeHighlighter(first, second, HighlighterLayer.FIRST - 1, null, HighlighterTargetArea.LINES_IN_RANGE);
    myHighlighterCount++;
    TextAttributes attr = getAttributesFor(range);
    highlighter.setErrorStripeMarkColor(attr.getErrorStripeColor());
    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
    highlighter.setLineMarkerRenderer(createRenderer(range));
    highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());
    final int line1 = myDocument.getLineNumber(first);
    final int line2 = myDocument.getLineNumber(second);
    final String tooltip;
    if (line1 == line2) {
      tooltip = VcsBundle.message("tooltip.text.line.changed", line1);
    }
    else {
      tooltip = VcsBundle.message("tooltip.text.lines.changed", line1, line2);
    }

    highlighter.setErrorStripeTooltip(tooltip);
    return highlighter;
  }


  private void removeHighlighters() {
    for (Range oldRange : myRanges) {
      removeHighlighter(oldRange.getHighlighter());
      oldRange.setHighlighter(null);
    }
  }

  synchronized void removeHighlighter(RangeHighlighter highlighter) {
    if (highlighter == null) return;
    MarkupModel markupModel = myDocument.getMarkupModel(myProject);
    //noinspection ConstantConditions
    if (markupModel == null) return;
    markupModel.removeHighlighter(highlighter);
    myHighlighterCount--;
  }

  private static TextAttributesKey getDiffColor(Range range) {
    switch (range.getType()) {
      case Range.INSERTED:
        return DiffColors.DIFF_INSERTED;
      case Range.DELETED:
        return DiffColors.DIFF_DELETED;
      case Range.MODIFIED:
        return DiffColors.DIFF_MODIFIED;
      default:
        assert false;
        return null;
    }
  }

  private static TextAttributesKey getEditorColorNameFor(Range range) {
    switch (range.getType()) {
      case Range.MODIFIED:
        return DiffColors.DIFF_MODIFIED;
      default:
        return DiffColors.DIFF_INSERTED;
    }
  }

  private static TextAttributes getAttributesFor(Range range) {
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = globalScheme.getAttributes(getEditorColorNameFor(range)).getErrorStripeColor();
    TextAttributes textAttributes = new TextAttributes(null, color, null, EffectType.BOXED, Font.PLAIN);
    textAttributes.setErrorStripeColor(color);
    return textAttributes;
  }

  private static void paintGutterFragment(Editor editor, Graphics g, Rectangle r, TextAttributesKey diffAttributeKey) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    final Color stripeColor = editor.getColorsScheme().getAttributes(diffAttributeKey).getErrorStripeColor();
    g.setColor(stripeColor);
    int endX = gutter.getWhitespaceSeparatorOffset();
    int x = r.x + r.width - 2;
    int width = endX - x;
    if (r.height > 0) {
      g.fillRect(x, r.y + 2, width, r.height - 4);
      g.setColor(gutter.getOutlineColor(false));
      UIUtil.drawLine(g, x, r.y + 2, x + width, r.y + 2);
      UIUtil.drawLine(g, x, r.y + 2, x, r.y + r.height - 3);
      UIUtil.drawLine(g, x, r.y + r.height - 3, x + width, r.y + r.height - 3);
    }
    else {
      int[] xPoints = new int[]{x,
        x,
        x + width - 1};
      int[] yPoints = new int[]{r.y - 4,
        r.y + 4,
        r.y};
      g.fillPolygon(xPoints, yPoints, 3);

      g.setColor(gutter.getOutlineColor(false));
      g.drawPolygon(xPoints, yPoints, 3);
    }
  }


  private LineMarkerRenderer createRenderer(final Range range) {
    return new ActiveGutterRenderer() {
      public void paint(Editor editor, Graphics g, Rectangle r) {
        paintGutterFragment(editor, g, r, getDiffColor(range));
      }

      public void doAction(Editor editor, MouseEvent e) {
        e.consume();
        JComponent comp = (JComponent)e.getComponent(); // shall be EditorGutterComponent, cast is safe.
        JLayeredPane layeredPane = comp.getRootPane().getLayeredPane();
        Point point = SwingUtilities.convertPoint(comp, ((EditorEx)editor).getGutterComponentEx().getWidth(), e.getY(), layeredPane);
        showActiveHint(range, editor, point);
      }

      public boolean canDoAction(final MouseEvent e) {
        final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
        return  e.getX() > gutter.getLineMarkerAreaOffset() + gutter.getIconsAreaWidth();
      }
    };
  }

  public synchronized void release() {
    try {
      if (!myIsInitialized) return;
      LOG.assertTrue(!myIsReleased);

      removeHighlighters();
      if (myDocumentListener != null) {
        myDocument.removeDocumentListener(myDocumentListener);
        myDocumentListener = null;
      }
    }
    finally {
      myIsReleased = true;
    }
  }

  public Document getDocument() {
    return myDocument;
  }

  public VirtualFile getVirtualFile() {
    return FileDocumentManager.getInstance().getFile(getDocument());
  }

  public List<Range> getRanges() {
    return myRanges;
  }

  public Document getUpToDateDocument() {
    return myUpToDateDocument;
  }

  public void startBulkUpdate() {
    myBulkUpdate = true;
    for (Range oldRange : myRanges) {
      removeHighlighter(oldRange.getHighlighter());
      oldRange.setHighlighter(null);
    }
    myRanges.clear();
  }

  public void finishBulkUpdate() {
    if (myBulkUpdate) {
      myBulkUpdate = false;
      reinstallRanges();
    }
  }

  private class MyDocumentListener extends DocumentAdapter {
    private int myFirstChangedLine;
    private int myUpToDateFirstLine;
    private int myUpToDateLastLine;
    private int myLastChangedLine;
    private int myLinesBeforeChange;

    public void beforeDocumentChange(DocumentEvent e) {
      if (myBulkUpdate) return;
      myFirstChangedLine = myDocument.getLineNumber(e.getOffset());
      myLastChangedLine = myDocument.getLineNumber(e.getOffset() + e.getOldLength());
      if (StringUtil.endsWithChar(e.getOldFragment(), '\n')) myLastChangedLine++;

      myLinesBeforeChange = myDocument.getLineNumber(e.getOffset() + e.getOldLength()) - myDocument.getLineNumber(e.getOffset());

      Range firstChangedRange = getLastRangeBeforeLine(myFirstChangedLine);

      if (firstChangedRange == null) {
        myUpToDateFirstLine = myFirstChangedLine;
      }
      else if (firstChangedRange.containsLine(myFirstChangedLine)) {
        myFirstChangedLine = firstChangedRange.getOffset1();
        myUpToDateFirstLine = firstChangedRange.getUOffset1();
      }
      else {
        myUpToDateFirstLine = firstChangedRange.getUOffset2() + (myFirstChangedLine - firstChangedRange.getOffset2());
      }

      Range myLastChangedRange = getLastRangeBeforeLine(myLastChangedLine);

      if (myLastChangedRange == null) {
        myUpToDateLastLine = myLastChangedLine;
      }
      else if (myLastChangedRange.containsLine(myLastChangedLine)) {
        myUpToDateLastLine = myLastChangedRange.getUOffset2();
        myLastChangedLine = myLastChangedRange.getOffset2();
      }
      else {
        myUpToDateLastLine = myLastChangedRange.getUOffset2() + (myLastChangedLine - myLastChangedRange.getOffset2());
      }

    }

    @Nullable
    private Range getLastRangeBeforeLine(int line) {
      Range result = null;
      for (Range range : myRanges) {
        if (range.isMoreThen(line)) return result;
        result = range;
      }
      return result;
    }

    public void documentChanged(DocumentEvent e) {
      if (myBulkUpdate) return;

      int line = myDocument.getLineNumber(e.getOffset() + e.getNewLength());
      int linesAfterChange = line - myDocument.getLineNumber(e.getOffset());
      int linesShift = linesAfterChange - myLinesBeforeChange;

      List<Range> rangesAfterChange = getRangesAfter(myLastChangedLine);
      List<Range> rangesBeforeChange = getRangesBefore(myFirstChangedLine);

      List<Range> changedRanges = getChangedRanges(myFirstChangedLine, myLastChangedLine);

      int newSize = rangesBeforeChange.size() + changedRanges.size() + rangesAfterChange.size();
      if (myRanges.size() != newSize) {
        LOG.info("Ranges: " + myRanges + "; first changed line: " + myFirstChangedLine + "; last changed line: " + myLastChangedLine);
        LOG.assertTrue(false);
      }


      myLastChangedLine += linesShift;


      List<Range> newChangedRanges = getNewChangedRanges();

      shiftRanges(rangesAfterChange, linesShift);

      if (!changedRanges.equals(newChangedRanges)) {
        replaceRanges(changedRanges, newChangedRanges);

        myRanges = new ArrayList<Range>();

        myRanges.addAll(rangesBeforeChange);
        myRanges.addAll(newChangedRanges);
        myRanges.addAll(rangesAfterChange);

        if (myHighlighterCount != myRanges.size()) {
          LOG.error("Highlighters: " + myHighlighterCount + ", ranges: " + myRanges.size());
        }

        myRanges = mergeRanges(myRanges);

        for (Range range : myRanges) {
          if (!range.hasHighlighter()) range.setHighlighter(createHighlighter(range));

        }

        if (myHighlighterCount != myRanges.size()) {
          LOG.error("Highlighters: " + myHighlighterCount + ", ranges: " + myRanges.size());
        }
      }

    }

    private List<Range> getNewChangedRanges() {
      List<String> lines = new DocumentWrapper(myDocument).getLines(myFirstChangedLine, myLastChangedLine);
      List<String> uLines = new DocumentWrapper(myUpToDateDocument)
        .getLines(myUpToDateFirstLine, myUpToDateLastLine);
      return new RangesBuilder(lines, uLines, myFirstChangedLine, myUpToDateFirstLine).getRanges();
    }

    private List<Range> mergeRanges(List<Range> ranges) {
      ArrayList<Range> result = new ArrayList<Range>();
      Iterator<Range> iterator = ranges.iterator();
      if (!iterator.hasNext()) return result;
      Range prev = iterator.next();
      while (iterator.hasNext()) {
        Range range = iterator.next();
        if (prev.canBeMergedWith(range)) {
          prev = prev.mergeWith(range, LineStatusTracker.this);
        }
        else {
          result.add(prev);
          prev = range;
        }
      }
      result.add(prev);
      return result;
    }

    private void replaceRanges(List<Range> rangesInChange, List<Range> newRangesInChange) {
      for (Range range : rangesInChange) {
        removeHighlighter(range.getHighlighter());
        range.setHighlighter(null);
      }
      for (Range range : newRangesInChange) {
        range.setHighlighter(createHighlighter(range));
      }
    }

    private void shiftRanges(List<Range> rangesAfterChange, int shift) {
      for (final Range aRangesAfterChange : rangesAfterChange) {
        aRangesAfterChange.shift(shift);
      }
    }

  }

  private List<Range> getChangedRanges(int from, int to) {
    return getChangedRanges(myRanges, from, to);
  }

  public static List<Range> getChangedRanges(List<Range> ranges, int from, int to) {
    ArrayList<Range> result = new ArrayList<Range>();
    for (Range range : ranges) {
      if (range.getOffset1() <= to && range.getOffset2() >= from) result.add(range);
//      if (range.getOffset1() > to) break;
    }
    return result;
  }

  private List<Range> getRangesBefore(int line) {
    return getRangesBefore(myRanges, line);

  }

  public static List<Range> getRangesBefore(List<Range> ranges, int line) {
    ArrayList<Range> result = new ArrayList<Range>();

    for (Range range : ranges) {
      if (range.getOffset2() < line) result.add(range);
      //if (range.getOffset2() > line) break;
    }
    return result;
  }

  private List<Range> getRangesAfter(int line) {
    return getRangesAfter(myRanges, line);
  }

  public static List<Range> getRangesAfter(List<Range> ranges, int line) {
    ArrayList<Range> result = new ArrayList<Range>();
    for (Range range : ranges) {
      if (range.getOffset1() > line) result.add(range);
    }
    return result;
  }

  public void moveToRange(final Range range, final Editor editor) {
    final int firstOffset = myDocument.getLineStartOffset(Math.min(range.getOffset1(), myDocument.getLineCount() - 1));
    editor.getCaretModel().moveToOffset(firstOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);

    editor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
      public void run() {
        Point p = editor.visualPositionToXY(editor.offsetToVisualPosition(firstOffset));
        JComponent editorComponent = editor.getContentComponent();
        JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
        p = SwingUtilities.convertPoint(editorComponent, 0, p.y, layeredPane);
        showActiveHint(range, editor, p);
      }
    });
  }

  @Nullable
  private Range getNextRange(Range range) {
    int index = myRanges.indexOf(range);
    if (index == myRanges.size() - 1) return null;
    return myRanges.get(index + 1);
  }

  @Nullable
  private Range getPrevRange(Range range) {
    int index = myRanges.indexOf(range);
    if (index <= 0) return null;
    return myRanges.get(index - 1);
  }

  @Nullable
  public Range getNextRange(int line) {
    final Range currentRange = getRangeForLine(line);
    if (currentRange != null) {
      return getNextRange(currentRange);
    }

    for (Range range : myRanges) {
      if (line > range.getOffset1() || line > range.getOffset2()) {
        continue;
      }
      return range;
    }
    return null;
  }

  @Nullable
  public Range getPrevRange(int line) {
    final Range currentRange = getRangeForLine(line);
    if (currentRange != null) {
      return getPrevRange(currentRange);
    }

    for (ListIterator<Range> iterator = myRanges.listIterator(myRanges.size()); iterator.hasPrevious();) {
      Range range = iterator.previous();
      if (range.getOffset1() > line) {
        continue;
      }
      return range;
    }
    return null;
  }

  @Nullable
  public Range getRangeForLine(final int line) {
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

  public static abstract class MyAction extends AnAction implements DumbAware {
    protected final LineStatusTracker myLineStatusTracker;
    protected final Range myRange;

    protected MyAction(String text, Icon icon, LineStatusTracker lineStatusTracker, Range range) {
      super(text, null, icon);
      myLineStatusTracker = lineStatusTracker;
      myRange = range;
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(isEnabled());
    }

    public abstract boolean isEnabled();

    protected int getMyRangeIndex() {
      List<Range> ranges = myLineStatusTracker.getRanges();
      for (int i = 0; i < ranges.size(); i++) {
        Range range = ranges.get(i);
        if (range.getOffset1() == myRange.getOffset1() && range.getOffset2() == myRange.getOffset2()) {
          return i;
        }
      }
      return -1;
    }
  }

  public static class RollbackAction extends LineStatusTracker.MyAction {
    public RollbackAction(LineStatusTracker lineStatusTracker, Range range, Editor editor) {
      super(VcsBundle.message("action.name.rollback"), IconLoader.getIcon("/actions/reset.png"), lineStatusTracker, range);
    }

    public boolean isEnabled() {
      return true;
    }

    public void actionPerformed(AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(myLineStatusTracker.getProject(), new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              if (!myLineStatusTracker.getDocument().isWritable()) {
                final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler
                  .getInstance(myLineStatusTracker.getProject()).ensureFilesWritable(myLineStatusTracker.getVirtualFile());
                if (operationStatus.hasReadonlyFiles()) return;
              }
              myLineStatusTracker.rollbackChanges(myRange);
            }
          });
        }
      }, VcsBundle.message("command.name.rollback.change"), null);

    }
  }

  public void rollbackChanges(Range range) {
    TextRange currentTextRange = getCurrentTextRange(range);

    if (range.getType() == Range.INSERTED) {
      myDocument
        .replaceString(currentTextRange.getStartOffset(), Math.min(currentTextRange.getEndOffset() + 1, myDocument.getTextLength()), "");
    }
    else if (range.getType() == Range.DELETED) {
      String upToDateContent = getUpToDateContent(range);
      myDocument.insertString(currentTextRange.getStartOffset(), upToDateContent);
    }
    else {

      String upToDateContent = getUpToDateContent(range);
      myDocument.replaceString(currentTextRange.getStartOffset(), Math.min(currentTextRange.getEndOffset() + 1, myDocument.getTextLength()),
                               upToDateContent);
    }
  }

  public String getUpToDateContent(Range range) {
    TextRange textRange = getUpToDateRange(range);
    final int startOffset = textRange.getStartOffset();
    final int endOffset = Math.min(textRange.getEndOffset() + 1, myUpToDateDocument.getTextLength());
    return myUpToDateDocument.getCharsSequence().subSequence(startOffset, endOffset).toString();
  }

  private Project getProject() {
    return myProject;
  }

  public class ShowDiffAction extends LineStatusTracker.MyAction {
    public ShowDiffAction(LineStatusTracker lineStatusTracker, Range range, Editor editor) {
      super(VcsBundle.message("action.name.show.difference"), IconLoader.getIcon("/actions/diff.png"), lineStatusTracker, range);
    }

    public boolean isEnabled() {
      return isModifiedRange() || isDeletedRange();
    }

    private boolean isDeletedRange() {
      return myRange.getType() == Range.DELETED;
    }

    private boolean isModifiedRange() {
      return myRange.getType() == Range.MODIFIED;
    }

    public void actionPerformed(AnActionEvent e) {
      DiffManager.getInstance().getDiffTool().show(createDiffData());
    }

    private DiffRequest createDiffData() {
      return new DiffRequest(myLineStatusTracker.getProject()) {
        public DiffContent[] getContents() {
          return new DiffContent[]{createDiffContent(myLineStatusTracker.getUpToDateDocument(),
                                                     myLineStatusTracker.getUpToDateRange(myRange), null),
            createDiffContent(myLineStatusTracker.getDocument(), myLineStatusTracker.getCurrentTextRange(myRange),
                              myLineStatusTracker.getVirtualFile())};
        }

        public String[] getContentTitles() {
          return new String[]{VcsBundle.message("diff.content.title.up.to.date"),
            VcsBundle.message("diff.content.title.current.range")};
        }

        public String getWindowTitle() {
          return VcsBundle.message("dialog.title.diff.for.range");
        }
      };
    }

    private DiffContent createDiffContent(final Document uDocument, TextRange textRange, VirtualFile file) {
      DiffContent diffContent = new DocumentContent(myProject, uDocument);
      return new FragmentContent(diffContent, textRange, myLineStatusTracker.getProject(), file);
    }
  }

  public class CopyAction extends MyAction {
    protected CopyAction(LineStatusTracker lineStatusTracker, Range range) {
      super(VcsBundle.message("action.name.copy.old.text"), IconLoader.getIcon("/actions/copy.png"), lineStatusTracker, range);
    }

    public boolean isEnabled() {
      return myRange.getType() == Range.DELETED || myRange.getType() == Range.MODIFIED;
    }

    public void actionPerformed(final AnActionEvent e) {
      final String content = myLineStatusTracker.getUpToDateContent(myRange);
      CopyPasteManager.getInstance().setContents(new StringSelection(content));
    }
  }

  private TextRange getCurrentTextRange(Range range) {
    return getRange(range.getType(), range.getOffset1(), range.getOffset2(), Range.DELETED, myDocument);
  }

  private TextRange getUpToDateRange(Range range) {
    return getRange(range.getType(), range.getUOffset1(), range.getUOffset2(), Range.INSERTED, myUpToDateDocument);
  }

  private static TextRange getRange(byte rangeType, int offset1, int offset2, byte emptyRangeCondition, Document document) {
    if (rangeType == emptyRangeCondition) {
      int lineStartOffset;
      if (offset1 == 0) {
        lineStartOffset = 0;
      }
      else {
        lineStartOffset = document.getLineEndOffset(offset1 - 1);
      }
      //if (lineStartOffset > 0) lineStartOffset--;
      return new TextRange(lineStartOffset, lineStartOffset);

    }
    else {
      int startOffset = document.getLineStartOffset(offset1);
      int endOffset = document.getLineEndOffset(offset2 - 1);
      if (startOffset > 0) {
        startOffset--;
        endOffset--;
      }
      return new TextRange(startOffset, endOffset);
    }
  }


  public void showActiveHint(Range range, final Editor editor, Point point) {

    DefaultActionGroup group = new DefaultActionGroup();

    final AnAction globalShowNextAction = ActionManager.getInstance().getAction("VcsShowNextChangeMarker");
    final AnAction globalShowPrevAction = ActionManager.getInstance().getAction("VcsShowPrevChangeMarker");

    final ShowPrevChangeMarkerAction localShowPrevAction = new ShowPrevChangeMarkerAction(getPrevRange(range), this, editor);
    final ShowNextChangeMarkerAction localShowNextAction = new ShowNextChangeMarkerAction(getNextRange(range), this, editor);

    JComponent editorComponent = editor.getComponent();

    localShowNextAction.registerCustomShortcutSet(localShowNextAction.getShortcutSet(), editorComponent);
    localShowPrevAction.registerCustomShortcutSet(localShowPrevAction.getShortcutSet(), editorComponent);

    group.add(localShowPrevAction);
    group.add(localShowNextAction);

    localShowNextAction.copyFrom(globalShowNextAction);
    localShowPrevAction.copyFrom(globalShowPrevAction);

    group.add(new RollbackAction(this, range, editor));
    group.add(new ShowDiffAction(this, range, editor));
    group.add(new CopyAction(this, range));

    final List<AnAction> actionList = (List<AnAction>)editorComponent.getClientProperty(AnAction.ourClientProperty);

    actionList.remove(globalShowPrevAction);
    actionList.remove(globalShowNextAction);

    JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, group, true).getComponent();

    final Color background = ((EditorEx)editor).getBackgroundColor();
    final Color foreground = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    toolbar.setBackground(background);

    toolbar.setBorder(new SideBorder2(foreground, foreground, range.getType() != Range.INSERTED ? null : foreground, foreground, 1));

    JPanel component = new JPanel(new BorderLayout());
    component.setOpaque(false);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.setOpaque(false);
    toolbarPanel.add(toolbar, BorderLayout.WEST);
    component.add(toolbarPanel, BorderLayout.NORTH);

    if (range.getType() != Range.INSERTED) {
      DocumentEx doc = (DocumentEx)myUpToDateDocument;
      EditorEx uEditor = (EditorEx)EditorFactory.getInstance().createViewer(doc, myProject);
      EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, getFileName());
      uEditor.setHighlighter(highlighter);

      EditorFragmentComponent editorFragmentComponent =
        EditorFragmentComponent.createEditorFragmentComponent(uEditor, range.getUOffset1(), range.getUOffset2(), false, false);

      component.add(editorFragmentComponent, BorderLayout.CENTER);
      EditorFactory.getInstance().releaseEditor(uEditor);
    }

    LightweightHint lightweightHint = new LightweightHint(component);
    lightweightHint.addHintListener(new HintListener() {
      public void hintHidden(EventObject event) {
        actionList.remove(localShowPrevAction);
        actionList.remove(localShowNextAction);
        actionList.add(globalShowPrevAction);
        actionList.add(globalShowNextAction);
      }
    });

    HintManagerImpl.getInstanceImpl().showEditorHint(lightweightHint, editor, point, HintManagerImpl.HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_TEXT_CHANGE |
                                                                             HintManagerImpl.HIDE_BY_OTHER_HINT | HintManagerImpl.HIDE_BY_SCROLLING,
                                                                             -1, false);
  }

  private String getFileName() {
    VirtualFile file = FileDocumentManager.getInstance().getFile(myDocument);
    if (file == null) return "";
    return file.getName();
  }

  public static LineStatusTracker createOn(Document doc, String upToDateContent, Project project) {
    Document document = EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(upToDateContent));
    final LineStatusTracker tracker = new LineStatusTracker(doc, document, project);
    tracker.initialize(upToDateContent);
    return tracker;
  }

  public static LineStatusTracker createOn(Document doc, Project project) {
    Document document = EditorFactory.getInstance().createDocument("");
    return new LineStatusTracker(doc, document, project);
  }

}
