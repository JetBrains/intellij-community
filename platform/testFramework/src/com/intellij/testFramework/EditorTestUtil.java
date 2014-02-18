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
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DefaultEditorTextRepresentationHelper;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * User: Maxim.Mossienko
 * Date: 15.03.2010
 * Time: 21:00:49
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
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    } else if (c == SMART_ENTER_FAKE_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    } else if (c == SMART_LINE_SPLIT_CHAR) {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_SPLIT);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    }
    else if (c == '\n') {
      EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
      actionHandler.execute(editor, DataManager.getInstance().getDataContext());
    }
    else {
      TypedAction action = actionManager.getTypedAction();
      action.actionPerformed(editor, c, DataManager.getInstance().getDataContext());
    }
  }

  public static void performReferenceCopy(DataContext dataContext) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(IdeActions.ACTION_COPY_REFERENCE);
    AnActionEvent
      event = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(),
                                            ActionManager.getInstance(), 0);
    action.update(event);
    Assert.assertTrue(event.getPresentation().isEnabled());
    action.actionPerformed(event);
  }

  public static void performPaste(Editor editor) {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
    actionHandler.execute(editor, DataManager.getInstance().getDataContext());
  }

  public static List<IElementType> getAllTokens(EditorHighlighter highlighter) {
    List<IElementType> tokens = new ArrayList<IElementType>();
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
  public static boolean configureSoftWraps(Editor editor, final int charCountToWrapAt) {
    int charWidthInPixels = 7;
    // we're adding 1 to charCountToWrapAt, to account for wrap character width, and 1 to overall width to overcome wrapping logic subtleties
    return configureSoftWraps(editor, (charCountToWrapAt + 1) * charWidthInPixels + 1, charWidthInPixels);
  }

  /**
   * Configures given editor to wrap at given width, assuming characters are of given width
   *
   * @return whether any actual wraps of editor contents were created as a result of turning on soft wraps
   */
  public static boolean configureSoftWraps(Editor editor, final int visibleWidth, final int charWidthInPixels) {
    editor.getSettings().setUseSoftWraps(true);
    SoftWrapModelImpl model = (SoftWrapModelImpl)editor.getSoftWrapModel();
    model.reinitSettings();

    SoftWrapApplianceManager applianceManager = model.getApplianceManager();
    applianceManager.setWidthProvider(new SoftWrapApplianceManager.VisibleAreaWidthProvider() {
      @Override
      public int getVisibleAreaWidth() {
        return visibleWidth;
      }
    });
    applianceManager.setRepresentationHelper(new DefaultEditorTextRepresentationHelper(editor) {
      @Override
      public int charWidth(char c, int fontType) {
        return charWidthInPixels;
      }
    });
    applianceManager.registerSoftWrapIfNecessary();
    return !model.getRegisteredSoftWraps().isEmpty();
  }

  /**
   * Equivalent to <code>extractCaretAndSelectionMarkers(document, true)</code>.
   *
   * @see #extractCaretAndSelectionMarkers(com.intellij.openapi.editor.Document, boolean)
   */
  public static CaretsState extractCaretAndSelectionMarkers(Document document) {
    return extractCaretAndSelectionMarkers(document, true);
  }

  /**
   * Removes &lt;caret&gt;, &lt;selection&gt; and &lt;/selection&gt; tags from document and returns a list of caret positions and selection
   * ranges for each caret. Both caret positions and selection ranges can be null in the returned data.
   *
   * Should be invoked in write action, as it modifies the document!
   *
   * @param processBlockSelection if <code>true</code>, &lt;block&gt; and &lt;/block&gt; tags describing a block selection state will also be extracted.
   */
  public static CaretsState extractCaretAndSelectionMarkers(Document document, boolean processBlockSelection) {
    CaretsState result = new CaretsState();

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

      result.carets.add(new Caret(caretMarker == null ? null : caretMarker.getStartOffset(),
                                  selStartMarker == null || selEndMarker == null
                                  ? null
                                  : new TextRange(selStartMarker.getStartOffset(), selEndMarker.getEndOffset())));

      pos = Math.max(caretMarker == null ? -1 : caretMarker.getStartOffset(), selEndMarker == null ? -1 : selEndMarker.getEndOffset());
    }
    if (result.carets.isEmpty()) {
      result.carets.add(new Caret(null, null));
    }
    if (blockSelectionStartMarker != null) {
      result.blockSelection = new TextRange(blockSelectionStartMarker.getStartOffset(), blockSelectionEndMarker.getStartOffset());
    }

    return result;
  }

  public static void verifyCaretAndSelectionState(Editor editor, CaretsState caretState) {
    CaretModel caretModel = editor.getCaretModel();
    List<com.intellij.openapi.editor.Caret> allCarets = new ArrayList<com.intellij.openapi.editor.Caret>(caretModel.getAllCarets());
    assertEquals("Unexpected number of carets", caretState.carets.size(), allCarets.size());
    for (int i = 0; i < caretState.carets.size(); i++) {
      String caretDescription = caretState.carets.size() == 1 ? "" : "caret " + (i + 1) + "/" + caretState.carets.size() + " ";
      com.intellij.openapi.editor.Caret currentCaret = allCarets.get(i);
      LogicalPosition actualCaretPosition = currentCaret.getLogicalPosition();
      LogicalPosition actualSelectionStart = editor.offsetToLogicalPosition(currentCaret.getSelectionStart());
      LogicalPosition actualSelectionEnd = editor.offsetToLogicalPosition(currentCaret.getSelectionEnd());
      EditorTestUtil.Caret expected = caretState.carets.get(i);
      if (expected.offset != null) {
        LogicalPosition expectedCaretPosition = editor.offsetToLogicalPosition(expected.offset);
        assertEquals(caretDescription + "unexpected caret position", expectedCaretPosition, actualCaretPosition);
      }
      if (expected.selection != null) {
        LogicalPosition expectedSelectionStart = editor.offsetToLogicalPosition(expected.selection.getStartOffset());
        LogicalPosition expectedSelectionEnd = editor.offsetToLogicalPosition(expected.selection.getEndOffset());

        assertEquals(caretDescription + "unexpected selection start", expectedSelectionStart, actualSelectionStart);
        assertEquals(caretDescription + "unexpected selection end", expectedSelectionEnd, actualSelectionEnd);
      }
      else {
        assertFalse(caretDescription + "should has no selection, but was: (" + actualSelectionStart + ", " + actualSelectionEnd + ")",
                    currentCaret.hasSelection());
      }
    }
  }

  public static void enableMultipleCarets() {
    Registry.get("editor.allow.multiple.carets").setValue(true);
  }

  public static void disableMultipleCarets() {
    Registry.get("editor.allow.multiple.carets").setValue(false);
  }

  public static class CaretsState {
    @NotNull
    public final List<Caret> carets = new ArrayList<Caret>();
    @Nullable
    public TextRange blockSelection;
  }

  public static class Caret {
    @Nullable
    public final Integer offset;
    @Nullable
    public final TextRange selection;

    public Caret(Integer offset, TextRange selection) {
      this.offset = offset;
      this.selection = selection;
    }
  }
}
