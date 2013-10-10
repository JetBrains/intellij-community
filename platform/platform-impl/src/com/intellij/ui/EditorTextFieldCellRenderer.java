package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.openapi.editor.impl.RangeMarkerTree;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public abstract class EditorTextFieldCellRenderer implements TableCellRenderer, Disposable {

  private static final String MY_PANEL_PROPERTY = "EditorTextFieldCellRenderer.MyEditorPanel";

  public EditorTextFieldCellRenderer(Disposable parent) {
    Disposer.register(parent, this);
  }

  protected abstract EditorColorsScheme getColorScheme();

  protected abstract String getText(JTable table, Object value, int row, int column);

  protected void customizeEditor(EditorEx editor, Object value, boolean selected, int row, int col) {
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    String text = getText(table, value, row, column);
    MyPanel panel = getEditorPanel(table);
    EditorEx editor = panel.editor;
    int tableFontSize = table.getFont().getSize();
    if (editor.getColorsScheme().getEditorFontSize() != tableFontSize) {
      editor.getColorsScheme().setEditorFontSize(tableFontSize);
    }
    setText(editor, text);

    if (isSelected) {
      ((EditorImpl)editor).setPaintSelection(true);
      editor.getColorsScheme().setColor(EditorColors.SELECTION_BACKGROUND_COLOR, table.getSelectionBackground());
      editor.getColorsScheme().setColor(EditorColors.SELECTION_FOREGROUND_COLOR, table.getSelectionForeground());
      editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
      editor.setBackgroundColor(table.getSelectionBackground());
    }
    else {
      ((EditorImpl)editor).setPaintSelection(false);
      editor.getSelectionModel().setSelection(0, 0);
      boolean selectedRow = table.getSelectedRowCount() > 0 && table.getSelectedRows()[table.getSelectedRowCount() - 1] == row;
      editor.setBackgroundColor(!selectedRow ? table.getBackground() : getColorScheme().getColor(EditorColors.CARET_ROW_COLOR));
    }
    customizeEditor(editor, value, isSelected, row, column);
    return panel;
  }

  @NotNull
  private MyPanel getEditorPanel(JTable table) {
    MyPanel panel = (MyPanel)table.getClientProperty(MY_PANEL_PROPERTY);
    if (panel != null) {
      EditorColorsScheme scheme = panel.editor.getColorsScheme();
      if (scheme instanceof DelegateColorScheme) {
        ((DelegateColorScheme)scheme).setDelegate(getColorScheme());
      }
      return panel;
    }

    // reuse EditorTextField initialization logic
    EditorTextField field = new EditorTextField(new MyDocument(), null, FileTypes.PLAIN_TEXT);
    field.setSupplementary(true);
    field.addNotify(); // creates editor

    EditorEx editor = (EditorEx)ObjectUtils.assertNotNull(field.getEditor());
    editor.setRendererMode(true);

    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(getColorScheme()));
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);

    editor.getScrollPane().setBorder(null);

    panel = new MyPanel(editor);
    Disposer.register(this, panel);

    table.putClientProperty(MY_PANEL_PROPERTY, panel);
    return panel;
  }

  @Override
  public void dispose() {
  }

  private static void setText(EditorEx editor, String text) {
    editor.getMarkupModel().removeAllHighlighters();

    editor.getDocument().setText(text);
    editor.getHighlighter().setText(text);
    ((EditorImpl)editor).resetSizes();
  }

  private static class MyPanel extends CellRendererPanel implements Disposable {
    EditorEx editor;

    public MyPanel(EditorEx editor) {
      add(editor.getContentComponent());
      this.editor = editor;
    }

    @Override
    public void dispose() {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  private static class MyDocument extends UserDataHolderBase implements DocumentEx {

    RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<RangeMarkerEx>(this) {};
    LineSet myLineSet = new LineSet();

    char[] myChars = ArrayUtil.EMPTY_CHAR_ARRAY;
    String myString = "";

    @Override
    public void setText(@NotNull CharSequence text) {
      String s = StringUtil.convertLineSeparators(text.toString());
      myChars = new char[s.length()];
      s.getChars(0, s.length(), myChars, 0);
      myString = new String(myChars);
      myLineSet.documentCreated(this);
    }

    @Override
    public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    }

    @NotNull
    @Override
    public LineIterator createLineIterator() {
      return myLineSet.createIterator();
    }

    @Override public void setModificationStamp(long modificationStamp) { }
    @Override public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) { }
    @Override public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) { }
    @Override public void replaceText(@NotNull CharSequence chars, long newModificationStamp) { }
    @Override public void moveText(int srcStart, int srcEnd, int dstOffset) { }
    @Override public int getListenersCount() { return 0; }
    @Override public void suppressGuardedExceptions() { }
    @Override public void unSuppressGuardedExceptions() { }
    @Override public boolean isInEventsHandling() { return false; }
    @Override public void clearLineModificationFlags() { }
    @Override public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) { return myRangeMarkers.removeInterval(rangeMarker); }

    @Override
    public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                    int start,
                                    int end,
                                    boolean greedyToLeft,
                                    boolean greedyToRight,
                                    int layer) {
      myRangeMarkers.addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, layer);
    }

    @Override public boolean isInBulkUpdate() { return false; }
    @Override public void setInBulkUpdate(boolean value) { }
    @NotNull @Override public List<RangeMarker> getGuardedBlocks() { return Collections.emptyList(); }
    @Override public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) { return myRangeMarkers.process(processor); }
    @Override public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) { return myRangeMarkers.processOverlappingWith(start, end, processor); }
    @Override public String getText() { return myString; }
    @NotNull @Override public String getText(@NotNull TextRange range) { return range.substring(getText()); }
    @NotNull @Override public CharSequence getCharsSequence() { return myString; }
    @NotNull @Override public char[] getChars() { return myChars; }
    @Override public int getTextLength() { return myChars.length; }
    @Override public int getLineCount() { return myLineSet.findLineIndex(myChars.length) + 1; }
    @Override public int getLineNumber(int offset) { return myLineSet.findLineIndex(offset); }
    @Override public int getLineStartOffset(int line) { return myChars.length == 0 ? 0 : myLineSet.getLineStart(line); }
    @Override public int getLineEndOffset(int line) { return myChars.length == 0? 0 : myLineSet.getLineEnd(line); }
    @Override public void insertString(int offset, @NotNull CharSequence s) { }
    @Override public void deleteString(int startOffset, int endOffset) { }
    @Override public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) { }
    @Override public boolean isWritable() { return false; }
    @Override public long getModificationStamp() { return 0; }
    @Override public void fireReadOnlyModificationAttempt() { }
    @Override public void addDocumentListener(@NotNull DocumentListener listener) { }
    @Override public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) { }
    @Override public void removeDocumentListener(@NotNull DocumentListener listener) { }
    @NotNull @Override public RangeMarker createRangeMarker(int startOffset, int endOffset) { return null; }
    @NotNull @Override public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) { return null; }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public void setReadOnly(boolean isReadOnly) { }
    @NotNull @Override public RangeMarker createGuardedBlock(int startOffset, int endOffset) { return null; }
    @Override public void removeGuardedBlock(@NotNull RangeMarker block) { }
    @Nullable @Override public RangeMarker getOffsetGuard(int offset) { return null; }
    @Nullable @Override public RangeMarker getRangeGuard(int start, int end) { return null; }
    @Override public void startGuardedBlockChecking() { }
    @Override public void stopGuardedBlockChecking() { }
    @Override public void setCyclicBufferSize(int bufferSize) { }

    @NotNull @Override public RangeMarker createRangeMarker(@NotNull TextRange textRange) { return null; }
    @Override public int getLineSeparatorLength(int line) { return 0; }
  }

}
