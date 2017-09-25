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
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.CurrentEditorProvider;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DefaultEditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.*;

/**
 * @author Maxim.Mossienko
 */
public class EditorTestUtil {
  public static final String CARET_TAG = "<caret>";
  public static final String CARET_TAG_PREFIX = CARET_TAG.substring(0, CARET_TAG.length() - 1);

  public static final String SELECTION_START_TAG = "<selection>";
  public static final String SELECTION_END_TAG = "</selection>";
  public static final String BLOCK_SELECTION_START_TAG = "<block>";
  public static final String BLOCK_SELECTION_END_TAG = "</block>";

  public static final char BACKSPACE_FAKE_CHAR = '\uFFFF';
  public static final char SMART_ENTER_FAKE_CHAR = '\uFFFE';
  public static final char SMART_LINE_SPLIT_CHAR = '\uFFFD';

  public static void performTypingAction(Editor editor, char c) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    if (c == BACKSPACE_FAKE_CHAR) {
      executeAction(editor, IdeActions.ACTION_EDITOR_BACKSPACE);
    } else if (c == SMART_ENTER_FAKE_CHAR) {
      executeAction(editor, IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
    } else if (c == SMART_LINE_SPLIT_CHAR) {
      executeAction(editor, IdeActions.ACTION_EDITOR_SPLIT);
    }
    else if (c == '\n') {
      executeAction(editor, IdeActions.ACTION_EDITOR_ENTER);
    }
    else {
      TypedAction action = actionManager.getTypedAction();
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
    action.beforeActionPerformedUpdate(event);
    if (!event.getPresentation().isEnabled()) {
      assertFalse("Action " + action + " is disabled", assertActionIsEnabled);
      return;
    }
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.fireBeforeActionPerformed(action, event.getDataContext(), event);
    action.actionPerformed(event);
    actionManager.fireAfterActionPerformed(action, event.getDataContext(), event);
  }

  @NotNull
  private static DataContext createEditorContext(@NotNull Editor editor) {
    Object hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
    Map<String, Object> map = ContainerUtil.newHashMap(Pair.create(CommonDataKeys.HOST_EDITOR.getName(), hostEditor),
                                                       Pair.createNonNull(CommonDataKeys.EDITOR.getName(), editor));
    DataContext parent = DataManager.getInstance().getDataContext(editor.getContentComponent());
    return SimpleDataContext.getSimpleContext(map, parent);
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
    int charWidthInPixels = 10;
    // we're adding 1 to charCountToWrapAt, to account for wrap character width, and 1 to overall width to overcome wrapping logic subtleties
    return configureSoftWraps(editor, (charCountToWrapAt + 1) * charWidthInPixels + 1, charWidthInPixels);
  }

  /**
   * Configures given editor to wrap at given width, assuming characters are of given width
   *
   * @return whether any actual wraps of editor contents were created as a result of turning on soft wraps
   */
  @TestOnly
  public static boolean configureSoftWraps(Editor editor, final int visibleWidth, final int charWidthInPixels) {
    editor.getSettings().setUseSoftWraps(true);
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
    applianceManager.setWidthProvider(() -> visibleWidth);
    model.setEditorTextRepresentationHelper(new DefaultEditorTextRepresentationHelper(editor) {
      @Override
      public int charWidth(int c, int fontType) {
        return charWidthInPixels;
      }
    });
    setEditorVisibleSizeInPixels(editor, visibleWidth, 1000);
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
    return new WriteCommandAction<CaretAndSelectionState>(null) {
      @Override
      public void run(@NotNull Result<CaretAndSelectionState> actionResult) {
        actionResult.setResult(extractCaretAndSelectionMarkersImpl(document, processBlockSelection));
      }
    }.execute().getResultObject();
  }

  @NotNull
  public static CaretAndSelectionState extractCaretAndSelectionMarkersImpl(@NotNull Document document, boolean processBlockSelection) {
    List<CaretInfo> carets = ContainerUtil.newArrayList();
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
    return new CaretAndSelectionState(Arrays.asList(carets.toArray(new CaretInfo[carets.size()])), blockSelection);
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
        assertFalse(messageSuffix + caretDescription + "should has no selection, but was: (" + actualSelectionStart + ", " + actualSelectionEnd + ")",
                    currentCaret.hasSelection());
      }
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
    return editor.getInlayModel().addInlineElement(offset, relatesToPrecedingText, new EditorCustomElementRenderer() {
      @Override
      public int calcWidthInPixels(@NotNull Editor editor) { return 1; }

      @Override
      public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {}
    });
  }

  public static void waitForLoading(Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (editor == null) return;
    while (!AsyncEditorLoader.isEditorLoaded(editor)) {
      LockSupport.parkNanos(100_000_000);
      UIUtil.dispatchAllInvocationEvents();
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

  public static class CaretAndSelectionState {
    public final List<CaretInfo> carets;
    public final TextRange blockSelection;

    public CaretAndSelectionState(List<CaretInfo> carets, @Nullable TextRange blockSelection) {
      this.carets = carets;
      this.blockSelection = blockSelection;
    }

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
}
