/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author gregsh
 */
public abstract class EditorTextFieldCellRenderer implements TableCellRenderer, Disposable {

  private static final Key<SimpleRendererComponent> MY_PANEL_PROPERTY = Key.create("EditorTextFieldCellRenderer.MyEditorPanel");

  private final Project myProject;
  private final FileType myFileType;
  private final boolean myInheritFontFromLaF;

  protected EditorTextFieldCellRenderer(@Nullable Project project, @Nullable FileType fileType, @NotNull Disposable parent) {
    this(project, fileType, true, parent);
  }

  private EditorTextFieldCellRenderer(@Nullable Project project, @Nullable FileType fileType,
                                      boolean inheritFontFromLaF, @NotNull Disposable parent) {
    myProject = project;
    myFileType = fileType;
    myInheritFontFromLaF = inheritFontFromLaF;
    Disposer.register(parent, this);
  }

  protected abstract String getText(JTable table, Object value, int row, int column);

  @Nullable
  protected TextAttributes getTextAttributes(JTable table, Object value, int row, int column) {
    return null;
  }

  @NotNull
  protected EditorColorsScheme getColorScheme(final JTable table) {
    return getEditorPanel(table).getEditor().getColorsScheme();
  }

  protected void customizeEditor(@NotNull EditorEx editor, JTable table, Object value, boolean selected, int row, int column) {
    String text = getText(table, value, row, column);
    getEditorPanel(table).setText(text, getTextAttributes(table, value, row, column), selected);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
    RendererComponent panel = getEditorPanel(table);
    EditorEx editor = panel.getEditor();
    editor.getColorsScheme().setEditorFontSize(table.getFont().getSize());

    editor.getColorsScheme().setColor(EditorColors.SELECTION_BACKGROUND_COLOR, table.getSelectionBackground());
    editor.getColorsScheme().setColor(EditorColors.SELECTION_FOREGROUND_COLOR, table.getSelectionForeground());
    editor.setBackgroundColor(selected ? table.getSelectionBackground() : table.getBackground());
    panel.setSelected(!Comparing.equal(editor.getBackgroundColor(), table.getBackground()));

    panel.setBorder(null); // prevents double border painting when ExtendedItemRendererComponentWrapper is used

    customizeEditor(editor, table, value, selected, row, column);
    return panel;
  }

  @NotNull
  private RendererComponent getEditorPanel(final JTable table) {
    RendererComponent panel = UIUtil.getClientProperty(table, MY_PANEL_PROPERTY);
    if (panel != null) {
      DelegateColorScheme scheme = (DelegateColorScheme)panel.getEditor().getColorsScheme();
      scheme.setDelegate(EditorColorsUtil.getGlobalOrDefaultColorScheme());
      return panel;
    }

    panel = createRendererComponent(myProject, myFileType, myInheritFontFromLaF);
    Disposer.register(this, panel);
    Disposer.register(this, () -> UIUtil.putClientProperty(table, MY_PANEL_PROPERTY, null));

    table.putClientProperty(MY_PANEL_PROPERTY, panel);
    return panel;
  }

  @NotNull
  protected RendererComponent createRendererComponent(@Nullable Project project, @Nullable FileType fileType, boolean inheritFontFromLaF) {
    return new AbbreviatingRendererComponent(project, fileType, inheritFontFromLaF);
  }

  @Override
  public void dispose() {
  }

  public abstract static class RendererComponent extends CellRendererPanel implements Disposable {
    private final EditorEx myEditor;
    private final EditorTextField myTextField;
    TextAttributes myTextAttributes;
    private boolean mySelected;

    RendererComponent(Project project, @Nullable FileType fileType, boolean inheritFontFromLaF) {
      Pair<EditorTextField, EditorEx> pair = createEditor(project, fileType, inheritFontFromLaF);
      myTextField = pair.first;
      myEditor = pair.second;
      add(myEditor.getContentComponent());
    }

    public EditorEx getEditor() {
      return myEditor;
    }

    @NotNull
    private static Pair<EditorTextField, EditorEx> createEditor(Project project, @Nullable FileType fileType, boolean inheritFontFromLaF) {
      EditorTextField field = new EditorTextField(new MyDocument(), project, fileType, false, false);
      field.setSupplementary(true);
      field.setFontInheritedFromLAF(inheritFontFromLaF);
      field.addNotify(); // creates editor

      EditorEx editor = (EditorEx)ObjectUtils.assertNotNull(field.getEditor());
      editor.setRendererMode(true);

      editor.setColorsScheme(editor.createBoundColorSchemeDelegate(null));
      editor.getSettings().setCaretRowShown(false);

      editor.getScrollPane().setBorder(null);

      return Pair.create(field, editor);
    }

    public void setText(String text, @Nullable TextAttributes textAttributes, boolean selected) {
      myTextAttributes = textAttributes;
      mySelected = selected;
      setText(text);
    }

    public abstract void setText(String text);

    @Override
    public void setBackground(Color bg) {
      // allows for striped tables
      if (myEditor != null) {
        myEditor.setBackgroundColor(bg);
      }
      super.setBackground(bg);
    }

    @Override
    public void dispose() {
      remove(myEditor.getContentComponent());
      myTextField.removeNotify();
    }

    void setTextToEditor(String text) {
      myEditor.getMarkupModel().removeAllHighlighters();
      WriteAction.run(() -> myEditor.getDocument().setText(text));
      ((EditorImpl)myEditor).resetSizes();
      myEditor.getHighlighter().setText(text);
      if (myTextAttributes != null) {
        myEditor.getMarkupModel().addRangeHighlighter(0, myEditor.getDocument().getTextLength(),
                                                      HighlighterLayer.ADDITIONAL_SYNTAX, myTextAttributes, HighlighterTargetArea.EXACT_RANGE);
      }

      ((EditorImpl)myEditor).setPaintSelection(mySelected);
      SelectionModel selectionModel = myEditor.getSelectionModel();
      selectionModel.setSelection(0, mySelected ? myEditor.getDocument().getTextLength() : 0);
    }
  }

  public static class SimpleRendererComponent extends RendererComponent implements Disposable {
    public SimpleRendererComponent(Project project, @Nullable FileType fileType, boolean inheritFontFromLaF) {
      super(project, fileType, inheritFontFromLaF);
    }

    @Override
    public void setText(String text) {
      setTextToEditor(text);
    }
  }

  public static class AbbreviatingRendererComponent extends RendererComponent {
    private static final char ABBREVIATION_SUFFIX = '\u2026'; // 2026 '...'
    private static final char RETURN_SYMBOL = '\u23ce';

    private final StringBuilder myDocumentTextBuilder = new StringBuilder();

    private Dimension myPreferredSize;
    private String myRawText;

    public AbbreviatingRendererComponent(Project project, @Nullable FileType fileType, boolean inheritFontFromLaF) {
      super(project, fileType, inheritFontFromLaF);
    }

    @Override
    public void setText(String text) {
      myRawText = text;
      myPreferredSize = null;
    }

    @Override
    public Dimension getPreferredSize() {
      if (myPreferredSize == null) {
        int maxLineLength = 0;
        int linesCount = 0;

        for (LineTokenizer lt = new LineTokenizer(myRawText); !lt.atEnd(); lt.advance()) {
          maxLineLength = Math.max(maxLineLength, lt.getLength());
          linesCount++;
        }

        FontMetrics fontMetrics = ((EditorImpl)getEditor()).getFontMetrics(myTextAttributes != null ? myTextAttributes.getFontType() : Font.PLAIN);
        int preferredHeight = getEditor().getLineHeight() * Math.max(1, linesCount);
        int preferredWidth = fontMetrics.charWidth('m') * maxLineLength;

        Insets insets = getInsets();
        if (insets != null) {
          preferredHeight += insets.top + insets.bottom;
          preferredWidth += insets.left + insets.right;
        }

        myPreferredSize = new Dimension(preferredWidth, preferredHeight);
      }
      return myPreferredSize;
    }

    @Override
    protected void paintChildren(Graphics g) {
      updateText(g.getClipBounds());
      super.paintChildren(g);
    }

    private void updateText(Rectangle clip) {
      FontMetrics fontMetrics = ((EditorImpl)getEditor()).getFontMetrics(myTextAttributes != null ? myTextAttributes.getFontType() : Font.PLAIN);
      Insets insets = getInsets();
      int maxLineWidth = getWidth() - (insets != null ? insets.left + insets.right : 0);

      myDocumentTextBuilder.setLength(0);

      boolean singleLineMode = getHeight() / (float)getEditor().getLineHeight() < 1.1f;
      if (singleLineMode) {
        appendAbbreviated(myDocumentTextBuilder, myRawText, 0, myRawText.length(), fontMetrics, maxLineWidth, true);
      }
      else {
        int lineHeight = getEditor().getLineHeight();
        int firstVisibleLine = clip.y / lineHeight;
        float visibleLinesCountFractional = clip.height / (float)lineHeight;
        int linesToAppend = 1 + (int)visibleLinesCountFractional;

        LineTokenizer lt = new LineTokenizer(myRawText);
        for (int line = 0; !lt.atEnd() && line < firstVisibleLine; lt.advance(), line++) {
          myDocumentTextBuilder.append('\n');
        }

        for (int line = 0; !lt.atEnd() && line < linesToAppend; lt.advance(), line++) {
          int start = lt.getOffset();
          int end = start + lt.getLength();
          appendAbbreviated(myDocumentTextBuilder, myRawText, start, end, fontMetrics, maxLineWidth, false);
          if (lt.getLineSeparatorLength() > 0) {
            myDocumentTextBuilder.append('\n');
          }
        }
      }

      setTextToEditor(myDocumentTextBuilder.toString());
    }

    private static void appendAbbreviated(StringBuilder to, String text, int start, int end,
                                          FontMetrics metrics, int maxWidth, boolean replaceLineTerminators) {
      int abbreviationLength = abbreviationLength(text, start, end, metrics, maxWidth, replaceLineTerminators);

      if (!replaceLineTerminators) {
        to.append(text, start, start + abbreviationLength);
      }
      else {
        CharSequenceSubSequence subSeq = new CharSequenceSubSequence(text, start, start + abbreviationLength);
        for (LineTokenizer lt = new LineTokenizer(subSeq); !lt.atEnd(); lt.advance()) {
          to.append(subSeq, lt.getOffset(), lt.getOffset() + lt.getLength());
          if (lt.getLineSeparatorLength() > 0) {
            to.append(RETURN_SYMBOL);
          }
        }
      }

      if (abbreviationLength != end - start) {
        to.append(ABBREVIATION_SUFFIX);
      }
    }

    private static int abbreviationLength(String text, int start, int end, FontMetrics metrics, int maxWidth, boolean replaceSeparators) {
      if (metrics.charWidth('m') * (end - start) <= maxWidth) return end - start;

      int abbrWidth = metrics.charWidth(ABBREVIATION_SUFFIX);
      int abbrLength = 0;

      CharSequenceSubSequence subSeq = new CharSequenceSubSequence(text, start, end);
      for (LineTokenizer lt = new LineTokenizer(subSeq); !lt.atEnd(); lt.advance()) {
        for (int i = 0; i < lt.getLength(); i++, abbrLength++) {
          abbrWidth += metrics.charWidth(subSeq.charAt(lt.getOffset() + i));
          if (abbrWidth >= maxWidth) return abbrLength;
        }
        if (replaceSeparators && lt.getLineSeparatorLength() != 0) {
          abbrWidth += metrics.charWidth(RETURN_SYMBOL);
          if (abbrWidth >= maxWidth) return abbrLength;
          abbrLength += lt.getLineSeparatorLength();
        }
      }

      return abbrLength;
    }
  }

  private static class MyDocument extends DocumentImpl {
    public MyDocument() {
      super("");
    }

    @Override
    public void replaceText(@NotNull CharSequence chars, long newModificationStamp) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void moveText(int srcStart, int srcEnd, int dstOffset) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void insertString(int offset, @NotNull CharSequence s) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteString(int startOffset, int endOffset) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long getModificationStamp() {
      return 0;
    }

    @NotNull
    @Override
    public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @NotNull
    @Override
    public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
