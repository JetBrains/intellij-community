/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.font.TextHitInfo;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.*;

/**
 * Fixture wrapping the IDE source editor, providing convenience methods
 * for controlling the source editor and verifying editor state. Note that unlike
 * the IntelliJ Editor class, which is one per file, this fixture represents an
 * editor in the more traditional sense: a container for multiple files, so you
 * ask "the" editor its current file, to select text in that file, to switch to
 * a different file, etc.
 */
public class EditorFixture {
  public static final String CARET = "^";
  public static final String SELECT_BEGIN = "|>";
  public static final String SELECT_END = "<|";

  /**
   * Performs simulation of user events on <code>{@link #target}</code>
   */
  public final Robot robot;
  private final IdeFrameFixture myFrame;
  private final EditorTabsFixture tabs;
  private Editor myEditor;

  /**
   * Constructs a new editor fixture, tied to the given project
   */
  public EditorFixture(Robot robot, Editor editor) {
    this.robot = robot;
    myEditor = editor;
    myFrame = null;
    tabs = null;
  }

  /**
   * Returns the line number of the current caret position (0-based).
   *
   * @return the current 0-based line number, or -1 if there is no current file
   */
  public int getCurrentLineNumber() {
    //noinspection ConstantConditions
    return execute(new GuiQuery<Integer>() {
      @Override
      @Nullable
      protected Integer executeInEDT() {
        Editor editor = getEditor();
        if (editor != null) {
          CaretModel caretModel = editor.getCaretModel();
          Caret primaryCaret = caretModel.getPrimaryCaret();
          int offset = primaryCaret.getOffset();
          Document document = editor.getDocument();
          return document.getLineNumber(offset);
        }

        return -1;
      }
    });
  }

  /**
   * Returns the contents of the current line, or null if there is no
   * file open. The caret position is indicated by {@code ^}, and
   * the selection text range, if on the current line, is indicated by
   * the text inside {@code |> <|}.
   *
   * @param trim            if true, trim whitespace around the line
   * @param showPositions   if true, show the editor positions (carets, selection)
   * @param additionalLines 0, or a count for additional number of lines to include on each side of the current line
   * @return the text contents at the current caret position
   */
  @Nullable
  public String getCurrentLineContents(boolean trim, boolean showPositions, int additionalLines) {
    if (showPositions) {
      return getCurrentLineContents(trim, CARET, SELECT_BEGIN, SELECT_END, additionalLines);
    }
    else {
      return getCurrentLineContents(trim, null, null, null, additionalLines);
    }
  }

  /**
   * Returns the contents of the current line, or null if there is no
   * file open.
   *
   * @param trim        if true, trim whitespace around the line
   * @param caretString typically "^" which will insert "^" to indicate the
   *                    caret position. If null, the caret position is not shown.
   * @param selectBegin the text string to insert at the beginning of the selection boundary
   * @param selectEnd   the text string to insert at the end of the selection boundary
   * @return the text contents at the current caret position
   */
  @Nullable
  public String getCurrentLineContents(final boolean trim,
                                       @Nullable final String caret,
                                       @Nullable final String selectBegin,
                                       @Nullable final String selectEnd,
                                       final int additionalLines) {
    return execute(new GuiQuery<String>() {
      @Override
      @Nullable
      protected String executeInEDT() {
        Editor editor = getEditor();
        if (editor != null) {
          CaretModel caretModel = editor.getCaretModel();
          Caret primaryCaret = caretModel.getPrimaryCaret();
          int offset = primaryCaret.getOffset();
          int start = primaryCaret.getSelectionStart();
          int end = primaryCaret.getSelectionEnd();
          if (start == end) {
            start = -1;
            end = -1;
          }
          Document document = editor.getDocument();
          int lineNumber = document.getLineNumber(offset);
          int lineStart = document.getLineStartOffset(lineNumber);
          int lineEnd = document.getLineEndOffset(lineNumber);
          int lineCount = document.getLineCount();
          for (int i = 1; i <= additionalLines; i++) {
            if (lineNumber - i >= 0) {
              lineStart = document.getLineStartOffset(lineNumber - i);
            }
            if (lineNumber + i < lineCount) {
              lineEnd = document.getLineEndOffset(lineNumber + i);
            }
          }

          String line = document.getText(new TextRange(lineStart, lineEnd));
          offset -= lineStart;
          start -= lineStart;
          end -= lineStart;
          StringBuilder sb = new StringBuilder(line.length() + 10);
          for (int i = 0, n = line.length(); i < n; i++) {
            if (selectBegin != null && start == i) {
              sb.append(selectBegin);
            }
            if (caret != null && offset == i) {
              sb.append(caret);
            }
            sb.append(line.charAt(i));
            if (selectEnd != null && end == i + 1) {
              sb.append(selectEnd);
            }
          }
          String result = sb.toString();
          if (trim) {
            result = result.trim();
          }
          return result;
        }

        return null;
      }
    });
  }

  private Editor getEditor() {
    return myEditor;
  }

  public void setEditor(Editor editor) {
    myEditor = editor;
  }


  /**
   * Returns the contents of the current file, or null if there is no
   * file open. The caret position is indicated by {@code ^}, and
   * the selection text range, if on the current line, is indicated by
   * the text inside {@code |> <|}.
   *
   * @param showPositions if true, show the editor positions (carets, selection)
   * @return the text contents at the current caret position
   */
  @Nullable
  public String getCurrentFileContents(boolean showPositions) {
    if (showPositions) {
      return getCurrentFileContents(CARET, SELECT_BEGIN, SELECT_END);
    }
    else {
      return getCurrentFileContents(null, null, null);
    }
  }

  /**
   * Returns the contents of the current file, or null if there is no
   * file open.
   *
   * @param caretString typically "^" which will insert "^" to indicate the
   *                    caret position. If null, the caret position is not shown.
   * @param selectBegin the text string to insert at the beginning of the selection boundary
   * @param selectEnd   the text string to insert at the end of the selection boundary
   * @return the text contents at the current caret position
   */
  @Nullable
  public String getCurrentFileContents(@Nullable final String caret, @Nullable final String selectBegin, @Nullable final String selectEnd) {
    return execute(new GuiQuery<String>() {
      @Override
      @Nullable
      protected String executeInEDT() {
        Editor editor = getEditor();
        if (editor != null) {
          CaretModel caretModel = editor.getCaretModel();
          Caret primaryCaret = caretModel.getPrimaryCaret();
          int offset = primaryCaret.getOffset();
          int start = primaryCaret.getSelectionStart();
          int end = primaryCaret.getSelectionEnd();
          if (start == end) {
            start = -1;
            end = -1;
          }
          Document document = editor.getDocument();
          int lineStart = 0;
          int lineEnd = document.getTextLength();
          String text = document.getText(new TextRange(lineStart, lineEnd));
          StringBuilder sb = new StringBuilder(text.length() + 10);
          for (int i = 0, n = text.length(); i < n; i++) {
            if (selectBegin != null && start == i) {
              sb.append(selectBegin);
            }
            if (caret != null && offset == i) {
              sb.append(caret);
            }
            sb.append(text.charAt(i));
            if (selectEnd != null && end == i + 1) {
              sb.append(selectEnd);
            }
          }
          return sb.toString();
        }

        return null;
      }
    });
  }

  /**
   * Returns content of the current editor's document with a given range.
   *
   * @param startOffset the content which starting right after this offset
   * @param endOffset   the end of content, symbol on the endOffset will be not included
   * @return
   */
  public String getCurrentFileContents(int startOffset, int endOffset) {
    return execute(new GuiQuery<String>() {
      @Override
      @Nullable
      protected String executeInEDT() {
        Editor editor = getEditor();
        if (editor != null) {
          Document document = editor.getDocument();
          return document.getText(new TextRange(startOffset, endOffset));
        }
        return null;
      }
    });
  }

  /**
   * Type the given text into the editor
   *
   * @param text the text to type at the current editor position
   */
  public EditorFixture enterText(@NotNull final String text) {
    Component component = getFocusedEditor();
    if (component != null) {
      robot.enterText(text);
    }

    return this;
  }

  /**
   * Type the given text into the editor as if the user had typed it
   * with an IME (an input method editor)
   *
   * @param text the text to type at the current editor position
   */
  public EditorFixture enterImeText(@NotNull final String text) {
    final Component component = getFocusedEditor();
    if (component != null && !text.isEmpty()) {
      execute(new GuiTask() {
        @Override
        protected void executeInEDT() {
          // Simulate editing by sending the same IME events that we observe arriving from a real input method
          int characterCount = text.length();
          TextHitInfo caret = TextHitInfo.afterOffset(characterCount - 1);
          TextHitInfo visiblePosition = TextHitInfo.beforeOffset(0);
          AttributedCharacterIterator iterator = new AttributedString(text).getIterator();
          int id = InputMethodEvent.INPUT_METHOD_TEXT_CHANGED;
          InputMethodEvent event = new InputMethodEvent(component, id, iterator, characterCount, caret, visiblePosition);
          component.dispatchEvent(event);
        }
      });
    }

    return this;
  }

  /**
   * Press and release the given key as indicated by the {@code VK_} codes in {@link KeyEvent}.
   * Used to transfer key presses to the editor which may have an effect but does not insert text into
   * the editor (e.g. pressing an arrow key to move the caret)
   *
   * @param keyCode the key code to press
   */
  public EditorFixture typeKey(int keyCode) {
    Component component = getFocusedEditor();
    if (component != null) {
      new ComponentDriver(robot).pressAndReleaseKeys(component, keyCode);
    }
    return this;
  }

  /**
   * Press (but don't release yet) the given key as indicated by the {@code VK_} codes in {@link KeyEvent}.
   * Used to transfer key presses to the editor which may have an effect but does not insert text into
   * the editor (e.g. pressing an arrow key to move the caret)
   *
   * @param keyCode the key code to press
   */
  public EditorFixture pressKey(int keyCode) {
    Component component = getFocusedEditor();
    if (component != null) {
      new ComponentDriver(robot).pressKey(component, keyCode);
    }
    return this;
  }

  /**
   * Release the given key (as indicated by the {@code VK_} codes in {@link KeyEvent}) which
   * must be currently pressed by a previous call to {@link #pressKey(int)}.
   *
   * @param keyCode the key code
   */
  public EditorFixture releaseKey(int keyCode) {
    Component component = getFocusedEditor();
    if (component != null) {
      new ComponentDriver(robot).releaseKey(component, keyCode);
    }
    return this;
  }

  /**
   * Requests focus in the editor
   */
  public EditorFixture requestFocus() {
    getFocusedEditor();
    return this;
  }

  /**
   * Requests focus in the editor, waits and returns editor component
   */
  @Nullable
  private JComponent getFocusedEditor() {
    Editor editor = getEditor();
    //wait when TextEditor ContentComponent will showing
    pause(new Condition("Waiting for showing focused textEditor") {
      @Override
      public boolean test() {
        return editor.getContentComponent().isShowing();
      }
    }, SHORT_TIMEOUT);

    if (editor != null) {
      JComponent contentComponent = editor.getContentComponent();
      new ComponentDriver(robot).focusAndWaitForFocusGain(contentComponent);
      assertSame(contentComponent, FocusManager.getCurrentManager().getFocusOwner());
      return contentComponent;
    }
    else {
      fail("Expected to find editor to focus, but there is no current editor");
      return null;
    }
  }

  /**
   * Moves the caret to the start of the given visual line number.
   *
   * @param lineNumber the line number.
   */
  @NotNull
  public EditorFixture moveToLine(final int lineNumber) {
    assertThat(lineNumber).isGreaterThanOrEqualTo(1);
    Integer offset = execute(new GuiQuery<Integer>() {
      @Override
      protected Integer executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          Document document = editor.getDocument();
          return document.getLineStartOffset(lineNumber - 1);
        }
        else {
          throw new Exception("Editor is null");
        }
      }
    });
    moveTo(offset);
    return this;
  }

  /**
   * Moves the caret to the given caret offset (0-based).
   *
   * @param offset the character offset.
   */
  public EditorFixture moveToAndClick(final int offset, MouseButton button) {
    assertThat(offset).isGreaterThanOrEqualTo(0);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        Editor editor = getEditor();
        assert editor != null;
        VisualPosition visualPosition = editor.offsetToVisualPosition(offset);
        Point point = editor.visualPositionToXY(visualPosition);
        Component editorComponent = robot.finder().find(editor.getComponent(), component -> component instanceof EditorComponentImpl);
        robot.click(editorComponent, point, button, 1);
      }
    });

    return this;
  }

  public EditorFixture moveTo(final int offset) {
    return moveToAndClick(offset, MouseButton.LEFT_BUTTON);
  }

  public EditorFixture rightClick(final int offset) {
    return moveToAndClick(offset, MouseButton.RIGHT_BUTTON);
  }

  /**
   * Selects the given range. If the first and second offsets are the same, it simply
   * moves the caret to the given position. The caret is always placed at the second offset,
   * <b>which is allowed to be smaller than the first offset</b>. Calling {@code select(10, 7)}
   * would be the same as dragging the mouse from offset 10 to offset 7 and releasing the mouse
   * button; the caret is now at the beginning of the selection.
   *
   * @param firstOffset  the character offset where we start the selection, or -1 to remove the selection
   * @param secondOffset the character offset where we end the selection, which can be an earlier
   *                     offset than the firstOffset
   */
  public EditorFixture select(final int firstOffset, final int secondOffset) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        // TODO: Do this via mouse drags!
        Editor editor = getEditor();
        if (editor != null) {
          editor.getCaretModel().getPrimaryCaret().setSelection(firstOffset, secondOffset);
          editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
      }
    });

    return this;
  }

  /**
   * Returns a list of pairs for selection ranges. If there is only one selection than return
   * list with one pair. And empty list if there is no selected range in editor.
   *
   * @throws EditorNotFoundException if no selected editor has been found
   */
  public List<Pair<Integer, Integer>> getSelection() {
    return execute(new GuiQuery<List<Pair<Integer, Integer>>>() {
      @Override
      protected List<Pair<Integer, Integer>> executeInEDT() throws Throwable {
        Editor editor = getEditor();
        if (editor != null) {
          int[] starts = editor.getSelectionModel().getBlockSelectionStarts();
          int[] ends = editor.getSelectionModel().getBlockSelectionEnds();
          assert (starts.length == ends.length);
          List<Pair<Integer, Integer>> result = new ArrayList<>(starts.length);
          for (int i = 0; i < starts.length; i++) {
            result.add(new Pair<>(starts[i], ends[i]));
          }
          return result;
        }

        throw new EditorNotFoundException("Unable to find selected editor to get selection");
      }
    });
  }


  /**
   * Finds the next position (or if {@code searchFromTop} is true, from the beginning) of
   * the given string indicated by a prefix and a suffix. The offset returned will be the position exactly
   * in the middle of the two. For example, if you have the text "The quick brown fox jumps over the lazy dog"
   * and you search via {@code moveTo("The qui", "ck brown", true)} the returned offset will be at the 7th
   * position in the string, between the "i" and "c".
   * <p/>
   * Note that on Windows, any {@code \r}'s are hidden from the editor, so they never count in offset
   * computations.
   *
   * @param prefix        the target prefix which must immediately precede the returned position
   * @param suffix        the target string, which must immediately follow the given prefix
   * @param searchFromTop if true, search from the beginning of the file instead of from the current editor position
   * @return the 0-based offset in the document, or -1 if not found.
   */
  public int findOffset(@Nullable final String prefix, @Nullable final String suffix, final boolean searchFromTop) {
    assertTrue(prefix != null || suffix != null);
    //noinspection ConstantConditions
    return execute(new GuiQuery<Integer>() {
      @Override
      @Nullable
      protected Integer executeInEDT() {
        Editor editor = getEditor();
        if (editor != null) {
          CaretModel caretModel = editor.getCaretModel();
          Caret primaryCaret = caretModel.getPrimaryCaret();
          Document document = editor.getDocument();
          String contents = document.getCharsSequence().toString();
          String target = (prefix != null ? prefix : "") + (suffix != null ? suffix : "");
          int targetIndex = contents.indexOf(target, searchFromTop ? 0 : primaryCaret.getOffset());
          return targetIndex != -1 ? targetIndex + (prefix != null ? prefix.length() : 0) : -1;
        }
        return -1;
      }
    });
  }

  /**
   * Finds the start position (if {@code returnStartIndex} is true or end position if {@code returnStartIndex} is false) in current editor's
   * document by regex.
   *
   * @param regex
   * @param returnStartIndex a flag that determines which index of found region should be returned: start index if it is true and end index if
   *                         it is false
   * @return the 0-based offset in the document, or -1 if not found.
   */
  public int findOffsetByRegex(@NotNull String regex, boolean returnStartIndex) {
    return execute(new GuiQuery<Integer>() {
      @Override
      protected Integer executeInEDT() {
        Editor editor = getEditor();
        if (editor != null) {
          String contents = editor.getDocument().getCharsSequence().toString();
          Matcher matcher = Pattern.compile(regex).matcher(contents);
          if (matcher.find()) {
            if (returnStartIndex) {
              return matcher.start();
            }
            else {
              return matcher.end();
            }
          }
        }
        return -1;
      }
    });
  }

  /**
   * Finds the first position in the editor document indicated by the given text segment, where ^ (or if not defined, |) indicates
   * the caret position.
   *
   * @param line the line segment to search for (with ^ or | indicating the caret position)
   * @return the 0-based offset in the document, or -1 if not found.
   */
  public int findOffset(@NotNull final String line) {
    int index = line.indexOf('^');
    if (index == -1) {
      // Also look for |. ^ has higher precedence since in many Android XML files we'll have | appearing as
      // the XML value flag delimiter.
      index = line.indexOf('|');
    }
    assertTrue("The text segment should contain a caret position indicated by ^ or |", index != -1);
    String prefix = line.substring(0, index);
    if (prefix.isEmpty()) {
      prefix = null;
    }
    String suffix = line.substring(index + 1);
    if (suffix.isEmpty()) {
      suffix = null;
    }
    assertTrue("The text segment should have more text than just the caret position", prefix != null || suffix != null);
    return findOffset(prefix, suffix, true);
  }


  /**
   * Selects the editor with a given tab name.
   */
  public EditorFixture selectTab(@NotNull final String tabName) {
    tabs.waitTab(tabName, 30).selectTab(tabName);
    return this;
  }

  /**
   * Closes the editor with a given tab name.
   */
  public EditorFixture closeTab(@NotNull final String tabName) {
    tabs.closeTab(tabName);
    return this;
  }

  public Boolean hasTab(@NotNull final String tabName) {
    return tabs.hasTab(tabName);
  }


  /**
   * Invokes the given action. This will look up the corresponding action's key bindings, if any, and invoke
   * it. It will fail if the action is not enabled, or if it is interactive.
   *
   * @param action the action to invoke
   */

  public EditorFixture invokeAction(@NotNull EditorAction action) {
    switch (action) {
      case DOWN:
        invokeActionViaKeystroke(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
        break;
      case BACK_SPACE:
        invokeActionViaKeystroke("EditorBackSpace");
        break;
      case UNDO:
        invokeActionViaKeystroke("$Undo");
        break;
      case REDO:
        invokeActionViaKeystroke("$Redo");
        break;
      case CUT:
        invokeActionViaKeystroke("$Cut");
        break;
      case COPY:
        invokeActionViaKeystroke("$Copy");
        break;
      case PASTE:
        invokeActionViaKeystroke("$Paste");
        break;
      case SELECT_ALL:
        invokeActionViaKeystroke("$SelectAll");
        break;
      case TAB: {
        invokeActionViaKeystroke(IdeActions.ACTION_EDITOR_TAB);
        break;
      }
      case ENTER: {
        invokeActionViaKeystroke(IdeActions.ACTION_EDITOR_ENTER);
        break;
      }
      case FORMAT: {
        // To format without showing dialog:
        //  invokeActionViaKeystroke("ReformatCode");
        // However, before we replace this, make sure the dialog isn't shown in some scenarios (e.g. first users)
        invokeActionViaKeystroke("ShowReformatFileDialog");
        JDialog dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
          @Override
          protected boolean isMatching(@NotNull JDialog dialog) {
            return dialog.isShowing() && dialog.getTitle().contains("Reformat");
          }
        });
        DialogFixture dialogFixture = new DialogFixture(robot, dialog);

        // Find and click the Run button. We can't just invoke
        //    dialogFixture.button("Run").click();
        // because that searches by button name (which is null for the Run button), not the button *title*.
        dialogFixture.button(new GenericTypeMatcher<JButton>(JButton.class) {
          @Override
          protected boolean isMatching(@NotNull JButton component) {
            return component.getText().equals("Run");
          }
        }).click();

        break;
      }
      case GOTO_DECLARATION:
        invokeActionViaKeystroke("GotoDeclaration");
        break;
      case COMPLETE_CURRENT_STATEMENT:
        invokeActionViaKeystroke("EditorCompleteStatement");
        break;
      case SAVE:
        invokeActionViaKeystroke("SaveAll");
        break;
      case TOGGLE_COMMENT:
        invokeActionViaKeystroke("CommentByLineComment");
        break;
      case DUPLICATE_LINES:
        invokeActionViaKeystroke("EditorDuplicate");
        break;
      case DELETE_LINE:
        invokeActionViaKeystroke("EditorDeleteLine");
        break;
      case NEXT_METHOD:
        invokeActionViaKeystroke("MethodDown");
        break;
      case PREVIOUS_METHOD:
        invokeActionViaKeystroke("MethodUp");
        break;
      case NEXT_ERROR:
        invokeActionViaKeystroke("GotoNextError");
        break;
      case PREVIOUS_ERROR:
        invokeActionViaKeystroke("GotoPreviousError");
        break;
      case JOIN_LINES:
        invokeActionViaKeystroke("EditorJoinLines");
        break;
      case SHOW_INTENTION_ACTIONS:
        invokeActionViaKeystroke("ShowIntentionActions");
        break;
      case RUN_FROM_CONTEXT:
        invokeActionViaKeystroke("RunClass");
        break;
      case EXTEND_SELECTION:
      case SHRINK_SELECTION:
        // Need to find the right action id's for these; didn't see them in the default keymap
      default:
        fail("Not yet implemented");
        break;
    }
    return this;
  }

  protected void invokeActionViaKeystroke(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    assertNotNull(actionId, action);
    assertTrue(actionId + " is not enabled", action.getTemplatePresentation().isEnabled());

    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    assertNotNull(shortcuts);
    assertThat(shortcuts).isNotEmpty();
    Shortcut shortcut = shortcuts[0];
    if (shortcut instanceof KeyboardShortcut) {
      KeyboardShortcut cs = (KeyboardShortcut)shortcut;
      KeyStroke firstKeyStroke = cs.getFirstKeyStroke();
      Component component = getFocusedEditor();
      if (component != null) {
        ComponentDriver driver = new ComponentDriver(robot);
        System.out.println("Invoking editor action " + actionId + " via shortcut "
                           + KeyEvent.getKeyModifiersText(firstKeyStroke.getModifiers())
                           + KeyEvent.getKeyText(firstKeyStroke.getKeyCode()));
        driver.pressAndReleaseKey(component, firstKeyStroke.getKeyCode(), new int[]{firstKeyStroke.getModifiers()});
        KeyStroke secondKeyStroke = cs.getSecondKeyStroke();
        if (secondKeyStroke != null) {
          System.out.println(" and "
                             + KeyEvent.getKeyModifiersText(secondKeyStroke.getModifiers())
                             + KeyEvent.getKeyText(secondKeyStroke.getKeyCode()));
          driver.pressAndReleaseKey(component, secondKeyStroke.getKeyCode(), new int[]{secondKeyStroke.getModifiers()});
        }
      }
      else {
        fail("Editor not focused for action");
      }
    }
    else {
      fail("Unsupported shortcut type " + shortcut.getClass().getName());
    }
  }


  /**
   * Invokes the show intentions action, waits for the actions to be displayed and then picks the
   * one with the given label prefix
   *
   * @param labelPrefix the prefix of the action description to be shown
   * @return this
   */
  @NotNull
  public EditorFixture invokeIntentionAction(@NotNull String labelPrefix) {
    invokeAction(EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS);
    JBListPopupFixture.clickPopupMenuItem(labelPrefix, true, null, robot, SHORT_TIMEOUT);
    return this;
  }

  /**
   * Returns a fixture around the layout editor, <b>if</b> the currently edited file
   * is a layout file and it is currently showing the layout editor tab or the parameter
   * requests that it be opened if necessary
   *
   * @param switchToTabIfNecessary if true, switch to the design tab if it is not already showing
   * @return a layout editor fixture, or null if the current file is not a layout file or the
   *     wrong tab is showing
   */
  //@Nullable
  //public LayoutEditorFixture getLayoutEditor(boolean switchToTabIfNecessary) {
  //  VirtualFile currentFile = getCurrentFile();
  //  if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.LAYOUT) {
  //    return null;
  //  }
  //
  //  if (switchToTabIfNecessary) {
  //    selectEditorView(Tab.DESIGN);
  //  }
  //
  //  return execute(new GuiQuery<LayoutEditorFixture>() {
  //    @Override
  //    @Nullable
  //    protected LayoutEditorFixture executeInEDT() throws Throwable {
  //      FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
  //      FileEditor[] editors = manager.getSelectedEditors();
  //      if (editors.length == 0) {
  //        return null;
  //      }
  //      FileEditor selected = editors[0];
  //      if (!(selected instanceof AndroidDesignerEditor)) {
  //        return null;
  //      }
  //
  //      return new LayoutEditorFixture(robot, (AndroidDesignerEditor)selected);
  //    }
  //  });
  //}

  /**
   * Returns a fixture around the layout preview window, <b>if</b> the currently edited file
   * is a layout file and it the XML editor tab of the layout is currently showing.
   *
   * @param switchToTabIfNecessary if true, switch to the editor tab if it is not already showing
   * @return a layout preview fixture, or null if the current file is not a layout file or the
   *     wrong tab is showing
   */
  //@Nullable
  //public LayoutPreviewFixture getLayoutPreview(boolean switchToTabIfNecessary) {
  //  VirtualFile currentFile = getCurrentFile();
  //  if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.LAYOUT) {
  //    return null;
  //  }
  //
  //  if (switchToTabIfNecessary) {
  //    selectEditorView(Tab.EDITOR);
  //  }
  //
  //  Boolean visible = GuiActionRunner.execute(new GuiQuery<Boolean>() {
  //    @Override
  //    protected Boolean executeInEDT() throws Throwable {
  //      AndroidLayoutPreviewToolWindowManager manager = AndroidLayoutPreviewToolWindowManager.getInstance(myFrame.getProject());
  //      return manager.getToolWindowForm() != null;
  //    }
  //  });
  //  if (visible == null || !visible) {
  //    myFrame.invokeMenuPath("View", "Tool Windows", "Preview");
  //  }
  //
  //  pause(new Condition("Preview window is visible") {
  //    @Override
  //    public boolean test() {
  //      AndroidLayoutPreviewToolWindowManager manager = AndroidLayoutPreviewToolWindowManager.getInstance(myFrame.getProject());
  //      return manager.getToolWindowForm() != null;
  //    }
  //  }, SHORT_TIMEOUT);
  //
  //  return new LayoutPreviewFixture(robot, myFrame.getProject());
  //}


  /**
   * Returns a fixture around the {@link com.android.tools.idea.editors.theme.ThemeEditor} <b>if</b> the currently
   * displayed editor is a theme editor.
   */
  //@NotNull
  //public ThemeEditorFixture getThemeEditor() {
  //  final ThemeEditorComponent themeEditorComponent =
  //    GuiTestUtil.waitUntilFound(robot, new GenericTypeMatcher<ThemeEditorComponent>(ThemeEditorComponent.class) {
  //      @Override
  //      protected boolean isMatching(@NotNull ThemeEditorComponent component) {
  //        return true;
  //      }
  //    });
  //
  //  return new ThemeEditorFixture(robot, themeEditorComponent);
  //}
  public EditorNotificationPanelFixture notificationPanel() {
    return EditorNotificationPanelFixture.Companion.findEditorNotificationPanel(robot, 30);
  }

  /**
   * Common editor actions, invokable via {@link #invokeAction(EditorAction)}
   */
  public enum EditorAction {
    DOWN,
    TAB,
    ENTER,
    SHOW_INTENTION_ACTIONS,
    FORMAT,
    SAVE,
    UNDO,
    REDO,
    COPY,
    PASTE,
    CUT,
    BACK_SPACE,
    COMPLETE_CURRENT_STATEMENT,
    EXTEND_SELECTION,
    SHRINK_SELECTION,
    SELECT_ALL,
    JOIN_LINES,
    DUPLICATE_LINES,
    DELETE_LINE,
    TOGGLE_COMMENT,
    GOTO_DECLARATION,
    NEXT_ERROR,
    PREVIOUS_ERROR,
    NEXT_METHOD,
    PREVIOUS_METHOD,
    RUN_FROM_CONTEXT
  }

  /**
   * The different tabs of an editor; used by for example {@link #open(VirtualFile, EditorFixture.Tab)} to indicate which
   * tab should be opened
   */
  public enum Tab {
    EDITOR, DESIGN, DEFAULT
  }

  public static class EditorNotFoundException extends Exception {

    public EditorNotFoundException(String message) {
      super(message);
    }
  }
}
