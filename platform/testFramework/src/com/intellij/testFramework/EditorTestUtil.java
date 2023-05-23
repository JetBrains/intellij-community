// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.nio.charset.Charset;
import java.util.List;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * @author Maxim.Mossienko
 */
public final class EditorTestUtil {
  public static final String CARET_TAG = "<caret>";
  public static final String CARET_TAG_PREFIX = CARET_TAG.substring(0, CARET_TAG.length() - 1);

  public static final String SELECTION_START_TAG = "<selection>";
  public static final String SELECTION_END_TAG = "</selection>";
  public static final String BLOCK_SELECTION_START_TAG = "<block>";
  public static final String BLOCK_SELECTION_END_TAG = "</block>";

  public static final char BACKSPACE_FAKE_CHAR = '\uFFFF';
  public static final char SMART_ENTER_FAKE_CHAR = '\uFFFE';
  public static final char SMART_LINE_SPLIT_CHAR = '\uFFFD';
  private static final Comparator<Pair<Integer, String>> MARKERS_COMPARATOR = (o1, o2) -> {
    int first = Comparing.compare(o1.first, o2.first);
    return first != 0 ? first : Comparing.compare(o1.second, o2.second);
  };

  public static void performTypingAction(@NotNull Editor editor, char c) {
    if (c == BACKSPACE_FAKE_CHAR) {
      executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
    }
    else if (c == SMART_ENTER_FAKE_CHAR) {
      executeAction(editor, IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
    }
    else if (c == SMART_LINE_SPLIT_CHAR) {
      executeAction(editor, IdeActions.ACTION_EDITOR_SPLIT);
    }
    else if (c == '\n') {
      executeAction(editor, IdeActions.ACTION_EDITOR_ENTER);
    }
    else {
      TypedAction action = TypedAction.getInstance();
      action.actionPerformed(editor, c, DataManager.getInstance().getDataContext(editor.getContentComponent()));
    }
  }

  public static void executeAction(@NotNull Editor editor, @NotNull String actionId) {
    executeAction(editor, actionId, false);
  }

  public static void executeAction(@NotNull Editor editor, @NotNull String actionId, boolean assertActionIsEnabled) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    AnAction action = actionManager.getAction(actionId);
    assertNotNull(action);
    executeAction(editor, assertActionIsEnabled, action);
  }

  public static void executeAction(@NotNull Editor editor, boolean assertActionIsEnabled, @NotNull AnAction action) {
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", createEditorContext(editor));
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
    else if (assertActionIsEnabled) {
      fail("Action " + action + " is disabled");
    }
  }

  @NotNull
  private static DataContext createEditorContext(@NotNull Editor editor) {
    Editor hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
    DataContext parent = DataManager.getInstance().getDataContext(editor.getContentComponent());
    return SimpleDataContext.builder()
      .setParent(parent)
      .add(CommonDataKeys.HOST_EDITOR, hostEditor)
      .add(CommonDataKeys.EDITOR, editor)
      .build();
  }

  public static void performReferenceCopy(Editor editor) {
    executeAction(editor, IdeActions.ACTION_COPY_REFERENCE, true);
  }

  public static void performPaste(Editor editor) {
    executeAction(editor, IdeActions.ACTION_EDITOR_PASTE, true);
  }

  public static List<IElementType> getAllTokens(EditorHighlighter highlighter) {
    List<IElementType> tokens = new ArrayList<>();
    HighlighterIterator iterator = highlighter.createIterator(0);
    while (!iterator.atEnd()) {
      tokens.add(iterator.getTokenType());
      iterator.advance();
    }
    return tokens;
  }

  public static void checkEditorHighlighter(Project project, Editor editor) {
    if (!(editor instanceof EditorImpl)) return;
    HighlighterIterator editorIterator = editor.getHighlighter().createIterator(0);

    EditorHighlighter freshHighlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
      project, editor.getVirtualFile());
    freshHighlighter.setEditor((EditorImpl)editor);
    freshHighlighter.setText(editor.getDocument().getImmutableCharSequence());
    HighlighterIterator freshIterator = freshHighlighter.createIterator(0);

    while (!editorIterator.atEnd() || !freshIterator.atEnd()) {
      if (editorIterator.atEnd() || freshIterator.atEnd()
          || editorIterator.getTokenType() != freshIterator.getTokenType()
          || editorIterator.getStart() != freshIterator.getStart()
          || editorIterator.getEnd() != freshIterator.getEnd()) {
        throw new IllegalStateException("Editor highlighter failed to update incrementally:\nFresh:  " +
                                        dumpHighlighter(freshHighlighter) +
                                        "\nEditor: " +
                                        dumpHighlighter(editor.getHighlighter()));
      }
      editorIterator.advance();
      freshIterator.advance();
    }
  }

  private static String dumpHighlighter(EditorHighlighter highlighter) {
    HighlighterIterator iterator = highlighter.createIterator(0);
    StringBuilder result = new StringBuilder();
    int i = 0;
    while (!iterator.atEnd()) {
      result.append(i).append(": ").append(iterator.getTokenType()).append(" [").append(iterator.getStart()).append("-")
        .append(iterator.getEnd()).append("], ");
      iterator.advance();
    }
    return result.toString();
  }

  public static int getCaretPosition(@NotNull final String content) {
    return getCaretAndSelectionPosition(content)[0];
  }

  public static int[] getCaretAndSelectionPosition(@NotNull final String content) {
    int caretPosInSourceFile = content.indexOf(CARET_TAG_PREFIX);
    int caretEndInSourceFile = content.indexOf(">", caretPosInSourceFile);
    int caretLength = caretEndInSourceFile - caretPosInSourceFile;
    int visualColumnOffset = 0;
    if (caretPosInSourceFile >= 0) {
      String visualOffsetString = content.substring(caretPosInSourceFile + CARET_TAG_PREFIX.length(), caretEndInSourceFile);
      if (visualOffsetString.length() > 1) {
        visualColumnOffset = Integer.parseInt(visualOffsetString.substring(1));
      }
    }
    int selectionStartInSourceFile = content.indexOf(SELECTION_START_TAG);
    int selectionEndInSourceFile = content.indexOf(SELECTION_END_TAG);
    if (selectionStartInSourceFile >= 0) {
      if (caretPosInSourceFile >= 0) {
        if (caretPosInSourceFile < selectionStartInSourceFile) {
          selectionStartInSourceFile -= caretLength;
          selectionEndInSourceFile -= caretLength;
        }
        else {
          if (caretPosInSourceFile < selectionEndInSourceFile) {
            caretPosInSourceFile -= SELECTION_START_TAG.length();
          }
          else {
            caretPosInSourceFile -= SELECTION_START_TAG.length() + SELECTION_END_TAG.length();
          }
        }
      }
      selectionEndInSourceFile -= SELECTION_START_TAG.length();
    }

    return new int[]{caretPosInSourceFile, visualColumnOffset, selectionStartInSourceFile, selectionEndInSourceFile};
  }

  /**
   * Configures given editor to wrap at given character count.
   *
   * @return whether any actual wraps of editor contents were created as a result of turning on soft wraps
   */
  @TestOnly
  public static boolean configureSoftWraps(Editor editor, final int charCountToWrapAt) {
    return configureSoftWraps(editor, charCountToWrapAt, true);
  }

  /**
   * Configures given editor to wrap at given character count.
   *
   * @return whether any actual wraps of editor contents were created as a result of turning on soft wraps
   */
  @TestOnly
  public static boolean configureSoftWraps(Editor editor, final int charCountToWrapAt, boolean useCustomSoftWrapIndent) {
    int charWidthInPixels = 10;
    // we're adding 1 to charCountToWrapAt, to account for wrap character width, and 1 to overall width to overcome wrapping logic subtleties
    return configureSoftWraps(editor, (charCountToWrapAt + 1) * charWidthInPixels + 1, 1000, charWidthInPixels, useCustomSoftWrapIndent);
  }

  @TestOnly
  public static boolean configureSoftWrapsAndViewport(Editor editor, int charCountToWrapAt, int visibleLineCount) {
    int charWidthInPixels = 10;
    // we're adding 1 to charCountToWrapAt, to account for wrap character width, and 1 to overall width to overcome wrapping logic subtleties
    return configureSoftWraps(editor, (charCountToWrapAt + 1) * charWidthInPixels + 1, visibleLineCount * editor.getLineHeight(),
                              charWidthInPixels);
  }

  @TestOnly
  public static boolean configureSoftWraps(Editor editor, final int visibleWidth, final int charWidthInPixels) {
    return configureSoftWraps(editor, visibleWidth, 1000, charWidthInPixels);
  }

  @TestOnly
  public static boolean configureSoftWraps(Editor editor, int visibleWidthInPixels, int visibleHeightInPixels, int charWidthInPixels) {
    return configureSoftWraps(editor, visibleWidthInPixels, visibleHeightInPixels, charWidthInPixels, true);
  }

  @TestOnly
  public static boolean configureSoftWraps(Editor editor, int visibleWidthInPixels, int visibleHeightInPixels, int charWidthInPixels, boolean useCustomSoftWrapIndent) {
    editor.getSettings().setUseSoftWraps(true);
    editor.getSettings().setUseCustomSoftWrapIndent(useCustomSoftWrapIndent);
    SoftWrapModelImpl model = (SoftWrapModelImpl)editor.getSoftWrapModel();
    model.setSoftWrapPainter(new SoftWrapPainter() {
      @Override
      public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
        return charWidthInPixels;
      }

      @Override
      public int getDrawingHorizontalOffset(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
        return charWidthInPixels;
      }

      @Override
      public int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType) {
        return charWidthInPixels;
      }

      @Override
      public boolean canUse() {
        return true;
      }

      @Override
      public void reinit() {}
    });
    model.reinitSettings();

    SoftWrapApplianceManager applianceManager = model.getApplianceManager();
    applianceManager.setWidthProvider(new TestWidthProvider(visibleWidthInPixels));
    setEditorVisibleSizeInPixels(editor, visibleWidthInPixels, visibleHeightInPixels);
    applianceManager.registerSoftWrapIfNecessary();
    return !model.getRegisteredSoftWraps().isEmpty();
  }

  public static void setEditorVisibleSize(Editor editor, int widthInChars, int heightInChars) {
    setEditorVisibleSizeInPixels(editor,
                                 widthInChars * EditorUtil.getSpaceWidth(Font.PLAIN, editor),
                                 heightInChars * editor.getLineHeight());
  }

  public static void setEditorVisibleSizeInPixels(Editor editor, int widthInPixels, int heightInPixels) {
    Dimension size = new Dimension(widthInPixels, heightInPixels);
    ((EditorEx)editor).getScrollPane().getViewport().setExtentSize(size);
  }

  /**
   * Equivalent to {@code extractCaretAndSelectionMarkers(document, true)}.
   *
   * @see #extractCaretAndSelectionMarkers(Document, boolean)
   */
  @NotNull
  public static CaretAndSelectionState extractCaretAndSelectionMarkers(@NotNull Document document) {
    return extractCaretAndSelectionMarkers(document, true);
  }

  /**
   * Removes &lt;caret&gt;, &lt;selection&gt; and &lt;/selection&gt; tags from document and returns a list of caret positions and selection
   * ranges for each caret. Both caret positions and selection ranges can be null in the returned data.
   *
   * @param processBlockSelection if {@code true}, &lt;block&gt; and &lt;/block&gt; tags describing a block selection state will also be extracted.
   */
  @NotNull
  public static CaretAndSelectionState extractCaretAndSelectionMarkers(@NotNull Document document, final boolean processBlockSelection) {
    return WriteCommandAction.writeCommandAction(null).compute(() -> extractCaretAndSelectionMarkersImpl(document, processBlockSelection));
  }

  @NotNull
  public static CaretAndSelectionState extractCaretAndSelectionMarkersImpl(@NotNull Document document, boolean processBlockSelection) {
    List<CaretInfo> carets = new ArrayList<>();
    String fileText = document.getText();

    RangeMarker blockSelectionStartMarker = null;
    RangeMarker blockSelectionEndMarker = null;
    if (processBlockSelection) {
      int blockSelectionStart = fileText.indexOf(BLOCK_SELECTION_START_TAG);
      int blockSelectionEnd = fileText.indexOf(BLOCK_SELECTION_END_TAG);
      if ((blockSelectionStart ^ blockSelectionEnd) < 0) {
        throw new IllegalArgumentException("Both block selection opening and closing tag must be present");
      }
      if (blockSelectionStart >= 0) {
        blockSelectionStartMarker = document.createRangeMarker(blockSelectionStart, blockSelectionStart);
        blockSelectionEndMarker = document.createRangeMarker(blockSelectionEnd, blockSelectionEnd);
        document.deleteString(blockSelectionStartMarker.getStartOffset(), blockSelectionStartMarker.getStartOffset() + BLOCK_SELECTION_START_TAG.length());
        document.deleteString(blockSelectionEndMarker.getStartOffset(), blockSelectionEndMarker.getStartOffset() + BLOCK_SELECTION_END_TAG.length());
      }
    }

    boolean multiCaret = StringUtil.getOccurrenceCount(document.getText(), CARET_TAG) > 1
                         || StringUtil.getOccurrenceCount(document.getText(), SELECTION_START_TAG) > 1;
    int pos = 0;
    while (pos < document.getTextLength()) {
      fileText = document.getText();
      int caretIndex = fileText.indexOf(CARET_TAG, pos);
      int selStartIndex = fileText.indexOf(SELECTION_START_TAG, pos);
      int selEndIndex = fileText.indexOf(SELECTION_END_TAG, pos);

      if ((selStartIndex ^ selEndIndex) < 0) {
        selStartIndex = -1;
        selEndIndex = -1;
      }
      if (0 <= selEndIndex && selEndIndex < selStartIndex) {
        throw new IllegalArgumentException("Wrong order of selection opening and closing tags");
      }
      if (caretIndex < 0 && selStartIndex < 0 && selEndIndex < 0) {
        break;
      }
      if (multiCaret && 0 <= caretIndex && caretIndex < selStartIndex) {
        selStartIndex = -1;
        selEndIndex = -1;
      }
      if (multiCaret && caretIndex > selEndIndex && selEndIndex >= 0) {
        caretIndex = -1;
      }

      final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
      final RangeMarker selStartMarker = selStartIndex >= 0
                                         ? document.createRangeMarker(selStartIndex, selStartIndex)
                                         : null;
      final RangeMarker selEndMarker = selEndIndex >= 0
                                       ? document.createRangeMarker(selEndIndex, selEndIndex)
                                       : null;

      if (caretMarker != null) {
        document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_TAG.length());
      }
      if (selStartMarker != null) {
        document.deleteString(selStartMarker.getStartOffset(),
                              selStartMarker.getStartOffset() + SELECTION_START_TAG.length());
      }
      if (selEndMarker != null) {
        document.deleteString(selEndMarker.getStartOffset(),
                              selEndMarker.getStartOffset() + SELECTION_END_TAG.length());
      }
      LogicalPosition caretPosition = null;
      if (caretMarker != null) {
        int line = document.getLineNumber(caretMarker.getStartOffset());
        int column = caretMarker.getStartOffset() - document.getLineStartOffset(line);
        caretPosition = new LogicalPosition(line, column);
      }
      carets.add(new CaretInfo(caretPosition,
                                      selStartMarker == null || selEndMarker == null
                                      ? null
                                      : new TextRange(selStartMarker.getStartOffset(), selEndMarker.getEndOffset())));

      pos = Math.max(caretMarker == null ? -1 : caretMarker.getStartOffset(), selEndMarker == null ? -1 : selEndMarker.getEndOffset());
    }
    if (carets.isEmpty()) {
      carets.add(new CaretInfo(null, null));
    }
    TextRange blockSelection = null;
    if (blockSelectionStartMarker != null) {
      blockSelection = new TextRange(blockSelectionStartMarker.getStartOffset(), blockSelectionEndMarker.getStartOffset());
    }
    return new CaretAndSelectionState(Arrays.asList(carets.toArray(new CaretInfo[0])), blockSelection);
  }

  /**
   * Applies given caret/selection state to the editor. Editor text must have been set up previously.
   */
  public static void setCaretsAndSelection(Editor editor, CaretAndSelectionState caretsState) {
    CaretModel caretModel = editor.getCaretModel();
    List<CaretState> states = new ArrayList<>(caretsState.carets.size());
    for (CaretInfo caret : caretsState.carets) {
      states.add(new CaretState(caret.position == null ? null : editor.offsetToLogicalPosition(caret.getCaretOffset(editor.getDocument())),
                                caret.selection == null ? null : editor.offsetToLogicalPosition(caret.selection.getStartOffset()),
                                caret.selection == null ? null : editor.offsetToLogicalPosition(caret.selection.getEndOffset())));
    }
    caretModel.setCaretsAndSelections(states);
    if (caretsState.blockSelection != null) {
      editor.getSelectionModel().setBlockSelection(editor.offsetToLogicalPosition(caretsState.blockSelection.getStartOffset()),
                                                   editor.offsetToLogicalPosition(caretsState.blockSelection.getEndOffset()));
    }
  }

  public static void verifyCaretAndSelectionState(Editor editor, CaretAndSelectionState caretState) {
    verifyCaretAndSelectionState(editor, caretState, null);
  }

  public static void verifyCaretAndSelectionState(Editor editor, CaretAndSelectionState caretState, String message) {
    boolean hasChecks = false;
    for (int i = 0; i < caretState.carets.size(); i++) {
      EditorTestUtil.CaretInfo expected = caretState.carets.get(i);
      if (expected.position != null || expected.selection != null) {
        hasChecks = true;
        break;
      }
    }
    if (!hasChecks) {
      return; // nothing to check, so we skip caret/selection assertions
    }

    try {
      doVerifyCaretAndSelectionState(editor, caretState, message);
    }
    catch (AssertionError e) {
      try {
        assertEquals(e.getMessage(), CaretAndSelectionMarkup.renderExpectedState(editor, caretState.carets),
                     CaretAndSelectionMarkup.renderActualState(editor));
      }
      catch (AssertionError exception) {
        exception.addSuppressed(e);
        throw exception;
      }
      throw e;
    }
  }

  private static void doVerifyCaretAndSelectionState(Editor editor, CaretAndSelectionState caretState, String message) {
    String messageSuffix = message == null ? "" : (message + ": ");
    CaretModel caretModel = editor.getCaretModel();
    List<Caret> allCarets = new ArrayList<>(caretModel.getAllCarets());
    assertEquals(messageSuffix + " Unexpected number of carets", caretState.carets.size(), allCarets.size());
    for (int i = 0; i < caretState.carets.size(); i++) {
      String caretDescription = caretState.carets.size() == 1 ? "" : "caret " + (i + 1) + "/" + caretState.carets.size() + " ";
      Caret currentCaret = allCarets.get(i);
      int actualCaretLine = editor.getDocument().getLineNumber(currentCaret.getOffset());
      int actualCaretColumn = currentCaret.getOffset() - editor.getDocument().getLineStartOffset(actualCaretLine);
      LogicalPosition actualCaretPosition = new LogicalPosition(actualCaretLine, actualCaretColumn);
      int selectionStart = currentCaret.getSelectionStart();
      int selectionEnd = currentCaret.getSelectionEnd();
      LogicalPosition actualSelectionStart = editor.offsetToLogicalPosition(selectionStart);
      LogicalPosition actualSelectionEnd = editor.offsetToLogicalPosition(selectionEnd);
      CaretInfo expected = caretState.carets.get(i);
      if (expected.position != null) {
        assertEquals(messageSuffix + caretDescription + "unexpected caret position", expected.position, actualCaretPosition);
      }
      if (expected.selection != null) {
        LogicalPosition expectedSelectionStart = editor.offsetToLogicalPosition(expected.selection.getStartOffset());
        LogicalPosition expectedSelectionEnd = editor.offsetToLogicalPosition(expected.selection.getEndOffset());

        assertEquals(messageSuffix + caretDescription + "unexpected selection start", expectedSelectionStart, actualSelectionStart);
        assertEquals(messageSuffix + caretDescription + "unexpected selection end", expectedSelectionEnd, actualSelectionEnd);
      }
      else {
        assertFalse(
          messageSuffix + caretDescription + "should has no selection, but was: (" + actualSelectionStart + ", " + actualSelectionEnd + ")",
          currentCaret.hasSelection());
      }
    }
  }

  /**
   * Runs syntax highlighter for the {@code testFile}, serializes highlighting results and comparing them with file from {@code answerFilePath}
   *
   * @param allowUnhandledTokens allows to have tokens without highlighting
   */
  public static void testFileSyntaxHighlighting(@NotNull PsiFile testFile, @NotNull String answerFilePath, boolean allowUnhandledTokens) {
    TestCase.assertNotNull("Fixture has no file", testFile);
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(testFile.getFileType(),
                                                                                              testFile.getProject(),
                                                                                              testFile.getVirtualFile());
    TestCase.assertNotNull("Syntax highlighter not found", syntaxHighlighter);
    final Lexer highlightingLexer = syntaxHighlighter.getHighlightingLexer();
    TestCase.assertNotNull("Highlighting lexer not found", highlightingLexer);

    final String fileText = testFile.getText();
    highlightingLexer.start(fileText);
    IElementType tokenType;
    final StringBuilder sb = new StringBuilder();
    Set<IElementType> notHighlightedTokens = new HashSet<>();
    while ((tokenType = highlightingLexer.getTokenType()) != null) {
      final TextAttributesKey[] highlights = syntaxHighlighter.getTokenHighlights(tokenType);
      if (highlights.length > 0) {
        if (sb.length() > 0) {
          sb.append("\n");
        }
        String token = fileText.substring(highlightingLexer.getTokenStart(), highlightingLexer.getTokenEnd());
        token = token.replace(' ', '␣');
        if (StringUtil.isEmptyOrSpaces(token)) {
          token = token.replace("\n", "\\n");
        }
        sb.append(token).append("\n");
        final List<String> attrNames = new SmartList<>();
        for (final TextAttributesKey attributesKey : highlights) {
          attrNames.add("    " + serializeTextAttributeKey(attributesKey));
        }
        sb.append(StringUtil.join(attrNames, "\n"));
      }
      else if (!StringUtil.isEmptyOrSpaces(highlightingLexer.getTokenText())) {
        notHighlightedTokens.add(tokenType);
      }
      highlightingLexer.advance();
    }
    if (!allowUnhandledTokens && !notHighlightedTokens.isEmpty()) {
      TestCase.fail("Some tokens have no highlighting: " + notHighlightedTokens);
    }
    UsefulTestCase.assertSameLinesWithFile(answerFilePath, sb.toString());
  }

  private static String serializeTextAttributeKey(@Nullable TextAttributesKey key) {
    if (key == null) {
      return "";
    }
    final String keyName = key.getExternalName();
    final TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
    TestCase.assertNotSame(fallbackKey, key);
    return fallbackKey == null ? keyName : (keyName + " => " + serializeTextAttributeKey(fallbackKey));
  }

  private static class CaretAndSelectionMarkup {
    private final @NotNull ArrayList<Pair<Integer, String>> marks = new ArrayList<>();

    static @NotNull String renderActualState(@NotNull Editor editor) {
      CaretAndSelectionMarkup markup = new CaretAndSelectionMarkup();
      // There's no guarantee on the order the carets are enumerated,
      // and in any case we should be prepared that something might go wrong.
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        boolean hasSelection = caret.hasSelection();
        if (hasSelection) markup.addMark(caret.getSelectionStart(), SELECTION_START_TAG);
        markup.addMark(caret.getOffset(), CARET_TAG);
        if (hasSelection) markup.addMark(caret.getSelectionEnd(), SELECTION_END_TAG);
      }
      return markup.insertMarks(editor.getDocument().getCharsSequence());
    }

    static @NotNull String renderExpectedState(@NotNull Editor editor, @NotNull List<? extends CaretInfo> carets) {
      CaretAndSelectionMarkup markup = new CaretAndSelectionMarkup();
      // The expected state is properly sorted already, so it doesn't require extra sorting,
      // but for sake of consistency we use the same approach as for the actual caret state.
      for (CaretInfo expected : carets) {
        LogicalPosition position = expected.position;
        TextRange selection = expected.selection;

        if (selection != null) markup.addMark(selection.getStartOffset(), SELECTION_START_TAG);
        if (position != null) markup.addMark(editor.getDocument().getLineStartOffset(position.line) + position.column, CARET_TAG);
        if (selection != null) markup.addMark(selection.getEndOffset(), SELECTION_END_TAG);
      }
      return markup.insertMarks(editor.getDocument().getCharsSequence());
    }

    private void addMark(int offset, @NotNull String s) {
      Pair<Integer, String> mark = Pair.create(offset, s);
      marks.add(mark);
    }

    private @NotNull String insertMarks(@NotNull @NlsSafe CharSequence text) {
      StringBuilder sb = new StringBuilder(text);

      marks.sort(Comparator.comparingInt(mark -> mark.first));
      for (int i = marks.size() - 1; i >= 0; i--) {
        Pair<Integer, String> mark = marks.get(i);
        int offset = mark.first;
        if (0 <= offset && offset <= sb.length()) {
          sb.insert(offset, mark.second);
        }
        else {
          sb.insert(Math.max(0, Math.min(offset, sb.length())), "!!!" + mark.second + "@[" + offset + "]");
        }
      }

      return sb.toString();
    }
  }

  public static FoldRegion addFoldRegion(@NotNull Editor editor, final int startOffset, final int endOffset, final String placeholder, final boolean collapse) {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final Ref<FoldRegion> ref = new Ref<>();
    foldingModel.runBatchFoldingOperation(() -> {
      FoldRegion region = foldingModel.addFoldRegion(startOffset, endOffset, placeholder);
      assertNotNull(region);
      region.setExpanded(!collapse);
      ref.set(region);
    });
    return ref.get();
  }


  public static Inlay addInlay(@NotNull Editor editor, int offset) {
    return addInlay(editor, offset, false);
  }

  public static Inlay addInlay(@NotNull Editor editor, int offset, boolean relatesToPrecedingText) {
    return addInlay(editor, offset, relatesToPrecedingText, 1);
  }

  public static Inlay addInlay(@NotNull Editor editor, int offset, boolean relatesToPrecedingText, int widthInPixels) {
    return editor.getInlayModel().addInlineElement(offset, relatesToPrecedingText, new EmptyInlayRenderer(widthInPixels));
  }

  public static Inlay addBlockInlay(@NotNull Editor editor,
                                    int offset,
                                    boolean relatesToPrecedingText,
                                    boolean showAbove,
                                    int widthInPixels,
                                    Integer heightInPixels) {
    return addBlockInlay(editor, offset, relatesToPrecedingText, showAbove, false, widthInPixels, heightInPixels);
  }


  public static Inlay addBlockInlay(@NotNull Editor editor,
                                    int offset,
                                    boolean relatesToPrecedingText,
                                    boolean showAbove,
                                    boolean showWhenFolded,
                                    int widthInPixels,
                                    Integer heightInPixels) {
    return editor.getInlayModel().addBlockElement(offset,
                                                  new InlayProperties()
                                                    .relatesToPrecedingText(relatesToPrecedingText)
                                                    .showAbove(showAbove)
                                                    .showWhenFolded(showWhenFolded),
                                                  new EmptyInlayRenderer(widthInPixels, heightInPixels));
  }

  public static Inlay addAfterLineEndInlay(@NotNull Editor editor, int offset, int widthInPixels) {
    return editor.getInlayModel().addAfterLineEndElement(offset, false, new EmptyInlayRenderer(widthInPixels));
  }

  public static @Nullable CustomFoldRegion addCustomFoldRegion(@NotNull Editor editor, int startLine, int endLine) {
    return addCustomFoldRegion(editor, startLine, endLine, 1);
  }

  public static @Nullable CustomFoldRegion addCustomFoldRegion(@NotNull Editor editor, int startLine, int endLine, int heightInPixels) {
    return addCustomFoldRegion(editor, startLine, endLine, 0, heightInPixels);
  }

  public static @Nullable CustomFoldRegion addCustomFoldRegion(@NotNull Editor editor, int startLine, int endLine,
                                                               int widthInPixels, int heightInPixels) {
    CustomFoldRegion[] result = new CustomFoldRegion[1];
    FoldingModel model = editor.getFoldingModel();
    model.runBatchFoldingOperation(() -> {
      result[0] = model.addCustomLinesFolding(startLine, endLine, new EmptyCustomFoldingRenderer(widthInPixels, heightInPixels));
    });
    return result[0];
  }

  public static void waitForLoading(Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (EditorUtil.isRealFileEditor(editor)) {
      UIUtil.dispatchAllInvocationEvents(); // if editor is loaded synchronously,
                                            // background loading thread stays blocked in 'invokeAndWait' call
      while (!AsyncEditorLoader.isEditorLoaded(editor)) {
        LockSupport.parkNanos(100_000_000);
        UIUtil.dispatchAllInvocationEvents();
      }
    }
  }

  public static void testUndoInEditor(@NotNull Editor editor, @NotNull Runnable runnable) {
    FileEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    Project project = editor.getProject();
    assertNotNull(project);
    UndoManagerImpl undoManager = (UndoManagerImpl)UndoManager.getInstance(project);
    CurrentEditorProvider savedProvider = undoManager.getEditorProvider();
    undoManager.setEditorProvider(() -> fileEditor); // making undo work in test
    try {
      runnable.run();
    }
    finally {
      undoManager.setEditorProvider(savedProvider);
    }
  }

  /**
   * @see #getTextWithCaretsAndSelections(Editor, boolean, boolean)
   */
  @NotNull
  public static String getTextWithCaretsAndSelections(@NotNull Editor editor) {
    return getTextWithCaretsAndSelections(editor, true, true);
  }

  /**
   * @return a text from the {@code editor} with optional carets and selections markers.
   */
  @NotNull
  public static String getTextWithCaretsAndSelections(@NotNull Editor editor, boolean addCarets, boolean addSelections) {
    StringBuilder sb = new StringBuilder(editor.getDocument().getCharsSequence());
    ContainerUtil.reverse(editor.getCaretModel().getAllCarets()).forEach(
      caret -> ContainerUtil.reverse(getCaretMacros(caret, addCarets, addSelections)).forEach(
        pair -> sb.insert(pair.first, pair.second)));
    return sb.toString();
  }

  /**
   * Return macros describing a {@code caret}
   */
  @NotNull
  public static List<Pair<Integer, String>> getCaretMacros(@NotNull Caret caret, boolean position, boolean selection) {
    if (!position && !selection) {
      return Collections.emptyList();
    }

    boolean addSelection = selection && caret.hasSelection();
    List<Pair<Integer, String>> result = new ArrayList<>();
    if (addSelection) {
      result.add(Pair.create(caret.getSelectionStart(), SELECTION_START_TAG));
    }
    if (position) {
      result.add(Pair.create(caret.getOffset(), CARET_TAG));
    }
    if (addSelection) {
      result.add(Pair.create(caret.getSelectionEnd(), SELECTION_END_TAG));
    }
    result.sort(Pair.comparingByFirst());
    return result;
  }

  /**
   * Loads file from the {@code sourcePath}, runs highlighting, collects highlights optionally filtered with {@code textAttributesKeysNames},
   * serializes them and compares result with file from {@code answersFilePath}. If answers file is missing, it's going to be created and
   * test will fail.
   *
   * @param acceptableKeyNames highlights filter by {@link TextAttributesKey#myExternalName key names} or null if all highlights should be collected
   * @apiNote If source file has carets in it, runs checking once per each caret. Results MUST be the same. E.g: brace matching highlighting with
   * cursor positioned on open and close brace.
   */
  public static void checkEditorHighlighting(@NotNull CodeInsightTestFixture fixture,
                                             @NotNull String answersFilePath,
                                             @Nullable Set<String> acceptableKeyNames) {
    Editor editor = fixture.getEditor();
    CaretModel caretModel = editor.getCaretModel();
    List<Integer> offs = ContainerUtil.map(caretModel.getAllCarets(), Caret::getOffset);
    List<Integer> caretsOffsets = offs.isEmpty() ? List.of(-1) : offs;
    caretModel.removeSecondaryCarets();
    CharSequence documentSequence = InjectedLanguageEditorUtil.getTopLevelEditor(editor).getDocument().getCharsSequence();

    IdentifierHighlighterPassFactory.doWithHighlightingEnabled(fixture.getProject(), fixture.getProjectDisposable(), () -> {
      for (Integer caretsOffset : caretsOffsets) {
        if (caretsOffset != -1) {
          caretModel.moveToOffset(caretsOffset);
        }

        UsefulTestCase.assertSameLinesWithFile(
          answersFilePath,
          renderTextWithHighlightingInfos(fixture.doHighlighting(), documentSequence, acceptableKeyNames),
          () -> "Failed at:\n " +
                documentSequence.subSequence(0, caretsOffset) +
                "<caret>" +
                documentSequence.subSequence(caretsOffset, documentSequence.length()) +
                "\n");
      }});
  }

  private static @NotNull String renderTextWithHighlightingInfos(@NotNull List<? extends HighlightInfo> highlightInfos,
                                                                 @NotNull CharSequence documentSequence,
                                                                 @Nullable Set<String> acceptableKeyNames) {
    List<Pair<Integer, String>> sortedMarkers = highlightInfos.stream()
      .flatMap(it -> {
        String keyText = it.type.getAttributesKey().toString();
        if (acceptableKeyNames != null && !acceptableKeyNames.contains(keyText)) {
          return Stream.empty();
        }
        return Stream.of(
          Pair.create(it.getStartOffset(), "<" + keyText + ">"),
          Pair.create(it.getEndOffset(), "</" + keyText + ">")
        );
      })
      .sorted(MARKERS_COMPARATOR).toList();

    StringBuilder sb = new StringBuilder();
    int lastEnd = 0;

    for (Pair<Integer, String> marker : sortedMarkers) {
      Integer startOffset = marker.first;
      if (startOffset > lastEnd) {
        sb.append(documentSequence.subSequence(lastEnd, startOffset));
        lastEnd = startOffset;
      }
      sb.append(marker.second);
    }
    return sb.append(documentSequence.subSequence(lastEnd, documentSequence.length())).toString();
  }


  public record CaretAndSelectionState(List<CaretInfo> carets, @Nullable TextRange blockSelection) {

    /**
     * Returns true if current CaretAndSelectionState contains at least one caret or selection explicitly specified
     */
    public boolean hasExplicitCaret() {
      if(carets.isEmpty()) return false;
      if(blockSelection == null && carets.size() == 1) {
        CaretInfo caret = carets.get(0);
        return caret.position != null || caret.selection != null;
      }
      return true;
    }
  }

  public static class CaretInfo {
    @Nullable
    public final LogicalPosition position; // column number in this position is calculated in terms of characters,
                                           // not in terms of visual position
                                           // so Tab character always increases the column number by 1
    @Nullable
    public final TextRange selection;

    public CaretInfo(@Nullable LogicalPosition position, @Nullable TextRange selection) {
      this.position = position;
      this.selection = selection;
    }

    public int getCaretOffset(Document document) {
      return position == null ? -1 : document.getLineStartOffset(position.line) + position.column;
    }
  }

  private static final class EmptyInlayRenderer implements EditorCustomElementRenderer {
    private final int width;
    private final Integer height;

    private EmptyInlayRenderer(int width) {
      this(width, null);
    }

    private EmptyInlayRenderer(int width, Integer height) {
      this.width = width;
      this.height = height;
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) { return width;}

    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
      return height == null ? EditorCustomElementRenderer.super.calcHeightInPixels(inlay) : height;
    }
  }

  private static class EmptyCustomFoldingRenderer implements CustomFoldRegionRenderer {
    private final int myWidth;
    private final int myHeight;

    private EmptyCustomFoldingRenderer(int width, int height) {
      myWidth = width;
      myHeight = height;
    }

    @Override
    public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
      return myWidth;
    }

    @Override
    public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
      return myHeight;
    }

    @Override
    public void paint(@NotNull CustomFoldRegion region,
                      @NotNull Graphics2D g,
                      @NotNull Rectangle2D targetRegion,
                      @NotNull TextAttributes textAttributes) {}
  }

  public static class TestWidthProvider implements SoftWrapApplianceManager.VisibleAreaWidthProvider {
    private int myWidth;

    public TestWidthProvider(int width) {
      setVisibleAreaWidth(width);
    }

    @Override
    public int getVisibleAreaWidth() {
      return myWidth;
    }

    public void setVisibleAreaWidth(int width) {
      myWidth = width;
    }
  }

  public static <E extends Exception> void saveEncodingsIn(@NotNull Project project, Charset newIdeCharset, Charset newProjectCharset, @NotNull ThrowableRunnable<E> task) throws E {
    EncodingManager encodingManager = EncodingManager.getInstance();
    String oldIde = encodingManager.getDefaultCharsetName();
    if (newIdeCharset != null) {
      encodingManager.setDefaultCharsetName(newIdeCharset.name());
    }

    EncodingProjectManager encodingProjectManager = EncodingProjectManager.getInstance(project);
    String oldProject = encodingProjectManager.getDefaultCharsetName();
    if (newProjectCharset != null) {
      encodingProjectManager.setDefaultCharsetName(newProjectCharset.name());
    }

    try {
      task.run();
    }
    finally {
      if (newIdeCharset != null) {
        encodingManager.setDefaultCharsetName(oldIde);
      }
      if (newProjectCharset != null) {
        encodingProjectManager.setDefaultCharsetName(oldProject);
      }
    }
  }}

