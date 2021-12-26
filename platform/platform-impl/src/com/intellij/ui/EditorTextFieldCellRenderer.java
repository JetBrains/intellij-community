// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.CaretModelImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorTextFieldRendererDocument;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author gregsh
 */
public abstract class EditorTextFieldCellRenderer implements TableCellRenderer, Disposable {

  private static final Key<SimpleRendererComponent> MY_PANEL_PROPERTY = Key.create("EditorTextFieldCellRenderer.MyEditorPanel");

  private final Project myProject;
  private final Language myLanguage;
  private final boolean myInheritFontFromLaF;

  /** @deprecated Use {@link EditorTextFieldCellRenderer#EditorTextFieldCellRenderer(Project, Language, Disposable)}*/
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  protected EditorTextFieldCellRenderer(@Nullable Project project, @Nullable FileType fileType, @NotNull Disposable parent) {
    this(project, fileType == null ? null : LanguageUtil.getFileTypeLanguage(fileType), true, parent);
  }

  protected EditorTextFieldCellRenderer(@Nullable Project project, @Nullable Language language, @NotNull Disposable parent) {
    this(project, language, true, parent);
  }

  private EditorTextFieldCellRenderer(@Nullable Project project, @Nullable Language language,
                                      boolean inheritFontFromLaF, @NotNull Disposable parent) {
    myProject = project;
    myLanguage = language;
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
    RendererComponent panel = ComponentUtil.getClientProperty(table, MY_PANEL_PROPERTY);
    if (panel != null) {
      DelegateColorScheme scheme = (DelegateColorScheme)panel.getEditor().getColorsScheme();
      scheme.setDelegate(EditorColorsUtil.getGlobalOrDefaultColorScheme());
      return panel;
    }

    panel = createRendererComponent(myProject, myLanguage, myInheritFontFromLaF);
    Disposer.register(this, panel);
    Disposer.register(this, () -> ComponentUtil.putClientProperty(table, MY_PANEL_PROPERTY, null));

    table.putClientProperty(MY_PANEL_PROPERTY, panel);
    return panel;
  }

  @NotNull
  protected RendererComponent createRendererComponent(@Nullable Project project, @Nullable Language language, boolean inheritFontFromLaF) {
    return new AbbreviatingRendererComponent(project, language, inheritFontFromLaF);
  }

  @Override
  public void dispose() {
  }

  public abstract static class RendererComponent extends CellRendererPanel implements Disposable {
    private final EditorEx myEditor;
    TextAttributes myTextAttributes;
    private boolean mySelected;

    RendererComponent(@Nullable Project project, @Nullable Language language, boolean inheritFontFromLaF) {
      myEditor = createEditor(project, language, inheritFontFromLaF);
      addEditorToSelf(myEditor);
      if (!UIUtil.isAncestor(this, myEditor.getContentComponent())) {
        throw new AssertionError("Editor component is not added in `addEditorToSelf`");
      }
    }

    public EditorEx getEditor() {
      return myEditor;
    }

    @NotNull
    private static EditorEx createEditor(Project project, @Nullable Language language, boolean inheritFontFromLaF) {
      Language adjustedLanguage = language != null ? language : PlainTextLanguage.INSTANCE;
      EditorTextFieldRendererDocument document = new EditorTextFieldRendererDocument();

      EditorEx editor = (EditorEx)EditorFactory.getInstance().createViewer(document, project);
      editor.putUserData(EditorTextField.SUPPLEMENTARY_KEY, true);

      EditorSettings settings = editor.getSettings();
      EditorTextField.setupTextFieldEditor(editor);

      editor.setRendererMode(true);
      editor.getScrollPane().setBorder(null);
      settings.setCaretRowShown(false);
      settings.setAnimatedScrolling(false);

      if (project != null) {
        EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
        LightVirtualFile virtualFile = new LightVirtualFile("_", adjustedLanguage, "");
        EditorHighlighter highlighter = highlighterFactory.createEditorHighlighter(project, virtualFile);
        editor.setHighlighter(highlighter);
      }

      EditorColorsScheme colorsScheme = editor.getColorsScheme();
      editor.setBackgroundColor(StartupUiUtil.isUnderDarcula()
                                ? UIUtil.getTextFieldBackground()
                                : colorsScheme.getDefaultBackground());
      if (inheritFontFromLaF) {
        ((EditorImpl)editor).setUseEditorAntialiasing(false);
        Font font = StartupUiUtil.getLabelFont();
        colorsScheme.setEditorFontName(font.getFontName());
        colorsScheme.setEditorFontSize(font.getSize());
      }
      else {
        UISettings uiSettings = UISettings.getInstance();
        if (uiSettings.getPresentationMode()) {
          editor.setFontSize(uiSettings.getPresentationModeFontSize());
        }
      }

      return editor;
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
      removeAll();
      EditorFactory.getInstance().releaseEditor(myEditor);
    }

    void addEditorToSelf(@NotNull EditorEx editor) {
      add(editor.getContentComponent());
    }

    void setTextToEditor(String text) {
      myEditor.getMarkupModel().removeAllHighlighters();
      myEditor.getDocument().setText(text);
      ((EditorImpl)myEditor).resetSizes();
      myEditor.getHighlighter().setText(text);
      if (myTextAttributes != null) {
        myEditor.getMarkupModel().addRangeHighlighter(0, myEditor.getDocument().getTextLength(),
                                                      HighlighterLayer.ADDITIONAL_SYNTAX, myTextAttributes, HighlighterTargetArea.EXACT_RANGE);
      }

      ((EditorImpl)myEditor).setPaintSelection(mySelected);
      SelectionModel selectionModel = myEditor.getSelectionModel();
      selectionModel.setSelection(0, mySelected ? myEditor.getDocument().getTextLength() : 0);
      ObjectUtils.consumeIfCast(myEditor.getCaretModel(), CaretModelImpl.class, CaretModelImpl::updateVisualPosition);
    }
  }

  public static class SimpleRendererComponent extends RendererComponent {
    public SimpleRendererComponent(Project project, @Nullable Language language, boolean inheritFontFromLaF) {
      super(project, language, inheritFontFromLaF);
    }

    @Override
    public void setText(String text) {
      setTextToEditor(text);
    }
  }

  public static class SimpleWithGutterRendererComponent extends SimpleRendererComponent {
    public SimpleWithGutterRendererComponent(Project project, @Nullable Language language, boolean inheritFontFromLaF) {
      super(project, language, inheritFontFromLaF);
    }

    @Override
    void addEditorToSelf(@NotNull EditorEx editor) {
      add(editor.getComponent());
    }
  }

  public static class AbbreviatingRendererComponent extends RendererComponent {
    private static final char ABBREVIATION_SUFFIX = '\u2026'; // 2026 '...'
    private static final char RETURN_SYMBOL = '\u23ce';

    private final StringBuilder myDocumentTextBuilder = new StringBuilder();
    private boolean myAppendEllipsis;
    private final char myReturnSymbol;
    private boolean myForceSingleLine;

    private Dimension myPreferredSize;

    @NlsSafe
    private String myRawText;

    public AbbreviatingRendererComponent(Project project, @Nullable Language language, boolean inheritFontFromLaF) {
      this(project, language, inheritFontFromLaF, true);
    }

    public AbbreviatingRendererComponent(Project project, @Nullable Language language, boolean inheritFontFromLaF, boolean appendEllipsis) {
      this(project, language, inheritFontFromLaF, appendEllipsis, false);
    }

    public AbbreviatingRendererComponent(Project project,
                                         @Nullable Language language,
                                         boolean inheritFontFromLaF,
                                         boolean appendEllipsis,
                                         boolean forceSingleLine) {
      this(project, language, inheritFontFromLaF, appendEllipsis, forceSingleLine, RETURN_SYMBOL);
    }

    public AbbreviatingRendererComponent(Project project,
                                         @Nullable Language language,
                                         boolean inheritFontFromLaF,
                                         boolean appendEllipsis,
                                         boolean forceSingleLine,
                                         char returnSymbol) {
      super(project, language, inheritFontFromLaF);
      myAppendEllipsis = appendEllipsis;
      myReturnSymbol = returnSymbol;
      myForceSingleLine = forceSingleLine;
    }

    @Override
    public void setText(String text) {
      myRawText = text;
      myPreferredSize = null;
    }

    public void setForceSingleLine(boolean forceSingleLine) {
      myForceSingleLine = forceSingleLine;
      myPreferredSize = null;
    }

    public void setAppendEllipsis(boolean appendEllipsis) {
      myAppendEllipsis = appendEllipsis;
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

        int preferredHeight;
        int preferredWidth;
        if (myForceSingleLine) {
          preferredHeight = getEditor().getLineHeight();
          preferredWidth = fontMetrics.charWidth('m') * myRawText.length();
        }
        else {
          preferredHeight = getEditor().getLineHeight() * Math.max(1, linesCount);
          preferredWidth = fontMetrics.charWidth('m') * maxLineLength;
        }

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

      boolean singleLineMode = myForceSingleLine || getHeight() / (float)getEditor().getLineHeight() < 1.1f;
      if (singleLineMode) {
        appendAbbreviated(myDocumentTextBuilder, myRawText, 0, myRawText.length(), fontMetrics, maxLineWidth, true, myAppendEllipsis,
                          myReturnSymbol);
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
          appendAbbreviated(myDocumentTextBuilder, myRawText, start, end, fontMetrics, maxLineWidth, false, myAppendEllipsis,
                            myReturnSymbol);
          if (lt.getLineSeparatorLength() > 0) {
            myDocumentTextBuilder.append('\n');
          }
        }
      }

      setTextToEditor(myDocumentTextBuilder.toString());
    }

    private static void appendAbbreviated(StringBuilder to,
                                          String text,
                                          int start,
                                          int end,
                                          FontMetrics metrics,
                                          int maxWidth,
                                          boolean replaceLineTerminators,
                                          boolean appendEllipsis,
                                          char returnSymbol) {
      int abbreviationLength =
        abbreviationLength(text, start, end, metrics, maxWidth, replaceLineTerminators, appendEllipsis, returnSymbol);

      if (!replaceLineTerminators) {
        to.append(text, start, start + abbreviationLength);
      }
      else {
        CharSequenceSubSequence subSeq = new CharSequenceSubSequence(text, start, start + abbreviationLength);
        for (LineTokenizer lt = new LineTokenizer(subSeq); !lt.atEnd(); lt.advance()) {
          to.append(subSeq, lt.getOffset(), lt.getOffset() + lt.getLength());
          if (lt.getLineSeparatorLength() > 0) {
            to.append(returnSymbol);
          }
        }
      }

      if (appendEllipsis && abbreviationLength != end - start) {
        to.append(ABBREVIATION_SUFFIX);
      }
    }

    private static int abbreviationLength(String text,
                                          int start,
                                          int end,
                                          FontMetrics metrics,
                                          int maxWidth,
                                          boolean replaceSeparators,
                                          boolean appendEllipsis,
                                          char returnSymbol) {
      if (metrics.charWidth('m') * (end - start) <= maxWidth) return end - start;

      int abbrWidth = appendEllipsis ? metrics.charWidth(ABBREVIATION_SUFFIX) : 0;
      int abbrLength = 0;

      CharSequenceSubSequence subSeq = new CharSequenceSubSequence(text, start, end);
      for (LineTokenizer lt = new LineTokenizer(subSeq); !lt.atEnd(); lt.advance()) {
        for (int i = 0; i < lt.getLength(); i++, abbrLength++) {
          abbrWidth += metrics.charWidth(subSeq.charAt(lt.getOffset() + i));
          if (abbrWidth >= maxWidth) return abbrLength;
        }
        if (replaceSeparators && lt.getLineSeparatorLength() != 0) {
          abbrWidth += metrics.charWidth(returnSymbol);
          if (abbrWidth >= maxWidth) return abbrLength;
          abbrLength += lt.getLineSeparatorLength();
        }
      }

      return abbrLength;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleContextDelegate(super.getAccessibleContext()) {
          @Override
          protected Container getDelegateParent() {
            return AbbreviatingRendererComponent.this.getParent();
          }

          @Override
          public String getAccessibleName() {
            return myRawText;
          }
        };
      }
      return accessibleContext;
    }
  }
}
