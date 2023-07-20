// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;
import java.awt.datatransfer.StringSelection;

public class EditorComponentCaretListenerTest extends AbstractEditorTest {
  @Override
  protected boolean isRunInCommand() {
    // Otherwise undoing doesn't work
    return false;
  }


  /* Tests for the positioning of a single caret (no selections) */

  public void testCaretNotificationsDuringTyping() {
    @SuppressWarnings("SpellCheckingInspection")
    /*
     * ( 0th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * ( 1st) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * ...
     *                                  caret
     *                                    v
     * (10th) Lorem ipsum dolor sit amet, |consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *                                    ^
     *                the text to be typed: "unus duo tres "
     * ...
     * (29th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     */

    final int caretInitialLineIndex = 10;
    final int caretInitialColumnIndex = 28;
    final int caretInitialDot = caretInitialLineIndex * (LOREM_IPSUM.length() + 1) + caretInitialColumnIndex;

    final EditorImpl editor = initEditor(
      StringUtil.repeat(LOREM_IPSUM + '\n', 30),
      caretInitialLineIndex,
      caretInitialColumnIndex,
      caretInitialDot
    );

    doTestCaretNotifications(
      editor.getContentComponent(),
      new CaretAction(() -> type('u'), new CaretPos(caretInitialDot + 1, caretInitialDot + 1)),
      new CaretAction(() -> type('n'), new CaretPos(caretInitialDot + 2, caretInitialDot + 2)),
      new CaretAction(() -> type('u'), new CaretPos(caretInitialDot + 3, caretInitialDot + 3)),
      new CaretAction(() -> type('s'), new CaretPos(caretInitialDot + 4, caretInitialDot + 4)),
      new CaretAction(() -> type(' '), new CaretPos(caretInitialDot + 5, caretInitialDot + 5)),
      new CaretAction(() -> type('d'), new CaretPos(caretInitialDot + 6, caretInitialDot + 6)),
      new CaretAction(() -> type('u'), new CaretPos(caretInitialDot + 7, caretInitialDot + 7)),
      new CaretAction(() -> type('o'), new CaretPos(caretInitialDot + 8, caretInitialDot + 8)),
      new CaretAction(() -> type(' '), new CaretPos(caretInitialDot + 9, caretInitialDot + 9)),
      new CaretAction(() -> type('t'), new CaretPos(caretInitialDot + 10, caretInitialDot + 10)),
      new CaretAction(() -> type('r'), new CaretPos(caretInitialDot + 11, caretInitialDot + 11)),
      new CaretAction(() -> type('e'), new CaretPos(caretInitialDot + 12, caretInitialDot + 12)),
      new CaretAction(() -> type('s'), new CaretPos(caretInitialDot + 13, caretInitialDot + 13)),
      new CaretAction(() -> type(' '), new CaretPos(caretInitialDot + 14, caretInitialDot + 14))
    );
  }

  public void testCaretNotificationsOfCaretMovementsWithoutTextModifications() {
    @SuppressWarnings("SpellCheckingInspection")
    /*
     *     caret
     * (initial pos)
     *       v
     * (0th) |Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *       ^
     * Actions from here:
     *    1. Caret left x 2 (no notifications are expected)
     *    2. Caret up x 2 (no notifications are expected)
     *    3. Caret right x 5
     *    4. Caret down x 2
     *
     * (1st) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *          caret
     *     (after step 4)
     *            v
     * (2nd) Lorem| ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *            ^
     *      Actions from here:
     *        5. Caret up x 1
     *        6. Caret right x 6
     *        7. Caret down x 3
     *
     * (3rd) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                caret                                                                                                            caret
     *           (after step 7)                                                                                                   (after step 11)
     *                  v                                                                                                                v
     * (4th) Lorem ipsum| dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.|
     *                  ^                                                                                                                ^
     *            Actions from here:                                                                                               Actions from here:
     *               8. Caret up x 1                                                                                                 12. Caret right x 2 (no notifications are expected)
     *               9. Caret down x 1                                                                                               13. Caret down x 2 (no notifications are expected)
     *              10. Caret left x 6
     *              11. Move caret to the end of the line
     */

    final EditorImpl editor = initEditor(StringUtil.repeat(LOREM_IPSUM + '\n', 4) + LOREM_IPSUM, 0, 0, 0);

    //noinspection DuplicateExpressions
    doTestCaretNotifications(
      editor.getContentComponent(),

      // step 1
      new CaretAction(() -> left(),  null),
      new CaretAction(() -> left(),  null),

      // step 2
      new CaretAction(() -> up(),    null),
      new CaretAction(() -> up(),    null),

      // step 3
      new CaretAction(() -> right(), new CaretPos(1, 1)),
      new CaretAction(() -> right(), new CaretPos(2, 2)),
      new CaretAction(() -> right(), new CaretPos(3, 3)),
      new CaretAction(() -> right(), new CaretPos(4, 4)),
      new CaretAction(() -> right(), new CaretPos(5, 5)),

      // step 4
      new CaretAction(() -> down(),  new CaretPos(5  +     (LOREM_IPSUM.length() + 1), 5  +     (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> down(),  new CaretPos(5  + 2 * (LOREM_IPSUM.length() + 1), 5  + 2 * (LOREM_IPSUM.length() + 1))),

      // step 5
      new CaretAction(() -> up(),    new CaretPos(5  +     (LOREM_IPSUM.length() + 1), 5  +     (LOREM_IPSUM.length() + 1))),

      // step 6
      new CaretAction(() -> right(), new CaretPos(6  +     (LOREM_IPSUM.length() + 1), 6  +     (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> right(), new CaretPos(7  +     (LOREM_IPSUM.length() + 1), 7  +     (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> right(), new CaretPos(8  +     (LOREM_IPSUM.length() + 1), 8  +     (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> right(), new CaretPos(9  +     (LOREM_IPSUM.length() + 1), 9  +     (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> right(), new CaretPos(10 +     (LOREM_IPSUM.length() + 1), 10 +     (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> right(), new CaretPos(11 +     (LOREM_IPSUM.length() + 1), 11 +     (LOREM_IPSUM.length() + 1))),

      // step 7
      new CaretAction(() -> down(),  new CaretPos(11 + 2 * (LOREM_IPSUM.length() + 1), 11 + 2 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> down(),  new CaretPos(11 + 3 * (LOREM_IPSUM.length() + 1), 11 + 3 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> down(),  new CaretPos(11 + 4 * (LOREM_IPSUM.length() + 1), 11 + 4 * (LOREM_IPSUM.length() + 1))),

      // step 8
      new CaretAction(() -> up(),    new CaretPos(11 + 3 * (LOREM_IPSUM.length() + 1), 11 + 3 * (LOREM_IPSUM.length() + 1))),

      // step 9
      new CaretAction(() -> down(),  new CaretPos(11 + 4 * (LOREM_IPSUM.length() + 1), 11 + 4 * (LOREM_IPSUM.length() + 1))),

      // step 10
      new CaretAction(() -> left(),  new CaretPos(10 + 4 * (LOREM_IPSUM.length() + 1), 10 + 4 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> left(),  new CaretPos(9  + 4 * (LOREM_IPSUM.length() + 1), 9  + 4 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> left(),  new CaretPos(8  + 4 * (LOREM_IPSUM.length() + 1), 8  + 4 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> left(),  new CaretPos(7  + 4 * (LOREM_IPSUM.length() + 1), 7  + 4 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> left(),  new CaretPos(6  + 4 * (LOREM_IPSUM.length() + 1), 6  + 4 * (LOREM_IPSUM.length() + 1))),
      new CaretAction(() -> left(),  new CaretPos(5  + 4 * (LOREM_IPSUM.length() + 1), 5  + 4 * (LOREM_IPSUM.length() + 1))),

      // step 11
      new CaretAction(() -> end(),   new CaretPos(LOREM_IPSUM.length() + 4 * (LOREM_IPSUM.length() + 1), LOREM_IPSUM.length() + 4 * (LOREM_IPSUM.length() + 1))),

      // step 12
      new CaretAction(() -> right(), null),
      new CaretAction(() -> right(), null),

      // step 13
      new CaretAction(() -> down(),  null),
      new CaretAction(() -> down(),  null)
    );
  }

  public void testCaretNotificationsWithinEmptyEditor() {
    // Any attempts to move the caret within an empty editor aren't supposed to cause caret movements => no caret updates are expected.

    final EditorImpl editor = initEditor("", 0, 0, 0);

    doTestCaretNotifications(
      editor.getContentComponent(),
      new CaretAction(() -> left(),  null),
      new CaretAction(() -> right(), null),
      new CaretAction(() -> up(),    null),
      new CaretAction(() -> down(),  null),
      new CaretAction(() -> left(),  null),
      new CaretAction(() -> down(),  null),
      new CaretAction(() -> right(), null),
      new CaretAction(() -> up(),    null)
    );
  }

  public void testCaretNotificationsCausedByUndo() {
    // Undoing a modifying operation near the caret has to cause a caret update notification

    // The initial configuration is the same as the testCaretNotificationsDuringTyping's one
    final int caretInitialLineIndex = 10;
    final int caretInitialColumnIndex = 28;
    final int caretInitialDot = caretInitialLineIndex * (LOREM_IPSUM.length() + 1) + caretInitialColumnIndex;

    final EditorImpl editor = initEditor(
      StringUtil.repeat(LOREM_IPSUM + '\n', 30),
      caretInitialLineIndex,
      caretInitialColumnIndex,
      caretInitialDot
    );

    // copy "unus duo tres " to the clipboard
    CopyPasteManager.getInstance().setContents(new StringSelection("unus duo tres "));

    EditorTestUtil.testUndoInEditor(editor, () -> {
      doTestCaretNotifications(
        editor.getContentComponent(),
        new CaretAction(() -> paste(), new CaretPos(caretInitialDot + 14, caretInitialDot + 14)),
        new CaretAction(() -> undo(),  new CaretPos(caretInitialDot, caretInitialDot))
      );
    });
  }


  /* Tests for the positioning of the selection of a single caret */

  public void testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromTopLeft() {
    @SuppressWarnings("SpellCheckingInspection")
    /*
     *  dot and mark
     * (initial pos and after step 15)
     *       v
     * (0th) |$Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *       ^
     * Actions from here:
     *    1. Left with selection x 2 (no notifications are expected)
     *    2. Up with selection x 2 (no notifications are expected)
     *    3. Left with selection (no notifications are expected)
     *    4. Up with selection (no notifications are expected)
     *    5. Right with selection x 3
     *    6. Down with selection x 2
     *    7. Right with selection
     *    8. Down with selection
     *    9. Right with selection
     *   10. Down with selection x 2
     *
     * (1st) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (2nd) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (3rd) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (4th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *           dot
     *     (after step 10)
     *            v
     * (5th) Lorem$ ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *            ^
     *  Actions from here:
     *    11. Up with selection x 3
     *    12. Down with selection
     *    13. Up with selection x 2
     *    14. Left by word with selection
     *    15. Up with selection
     */

    final var editor = initEditor(StringUtil.repeat(LOREM_IPSUM + '\n', 5) + LOREM_IPSUM, 0, 0, 0);

    //noinspection DuplicateExpressions
    doTestCaretNotifications(
      editor.getContentComponent(),

      // step 1
      new CaretAction(() -> leftWithSelection(),                    null),
      new CaretAction(() -> leftWithSelection(),                    null),

      // step 2
      new CaretAction(() -> upWithSelection(),                      null),
      new CaretAction(() -> upWithSelection(),                      null),

      // step 3
      new CaretAction(() -> leftWithSelection(),                    null),

      // step 4
      new CaretAction(() -> upWithSelection(),                      null),

      // step 5
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(1, 0)),
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(2, 0)),
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(3, 0)),

      // step 6
      new CaretAction(() -> downWithSelection(),                    new CaretPos(3 +     (LOREM_IPSUM.length() + 1), 0)),
      new CaretAction(() -> downWithSelection(),                    new CaretPos(3 + 2 * (LOREM_IPSUM.length() + 1), 0)),

      // step 7
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(4 + 2 * (LOREM_IPSUM.length() + 1), 0)),

      // step 8
      new CaretAction(() -> downWithSelection(),                    new CaretPos(4 + 3 * (LOREM_IPSUM.length() + 1), 0)),

      // step 9
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(5 + 3 * (LOREM_IPSUM.length() + 1), 0)),

      // step 10
      new CaretAction(() -> downWithSelection(),                    new CaretPos(5 + 4 * (LOREM_IPSUM.length() + 1), 0)),
      new CaretAction(() -> downWithSelection(),                    new CaretPos(5 + 5 * (LOREM_IPSUM.length() + 1), 0)),

      // step 11
      new CaretAction(() -> upWithSelection(),                      new CaretPos(5 + 4 * (LOREM_IPSUM.length() + 1), 0)),
      new CaretAction(() -> upWithSelection(),                      new CaretPos(5 + 3 * (LOREM_IPSUM.length() + 1), 0)),
      new CaretAction(() -> upWithSelection(),                      new CaretPos(5 + 2 * (LOREM_IPSUM.length() + 1), 0)),

      // step 12
      new CaretAction(() -> downWithSelection(),                    new CaretPos(5 + 3 * (LOREM_IPSUM.length() + 1), 0)),

      // step 13
      new CaretAction(() -> upWithSelection(),                      new CaretPos(5 + 2 * (LOREM_IPSUM.length() + 1), 0)),
      new CaretAction(() -> upWithSelection(),                      new CaretPos(5 +     (LOREM_IPSUM.length() + 1), 0)),

      // step 14
      new CaretAction(() -> moveCaretToPreviousWordWithSelection(), new CaretPos(LOREM_IPSUM.length() + 1, 0)),

      // step 15
      new CaretAction(() -> upWithSelection(),                      new CaretPos(0, 0))
    );
  }

  public void testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromCenter() {
    @SuppressWarnings("SpellCheckingInspection")
    /*
     * (0th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (1st) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (2nd) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                              dot and mark
     *                    (initial pos and after step 10)
     *                                   v
     * (3rd) Lorem ipsum dolor sit amet, |$consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *                                   ^
     *                           Actions from here:
     *                              1. Left with selection x 6
     *                              2. Right with selection x 3
     *                              3. Up with selection
     *                              4. Down with selection
     *                              5. Up with selection x 2
     *                              6. Down with selection x 4
     *
     * (4th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                               dot
     *                          (after step 6)
     *                                v
     * (5th) Lorem ipsum dolor sit ame$t, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *                                ^
     *                        Actions from here:
     *                           7. Right with selection x 3
     *                           8. Right by word with selection
     *                           9. Left by word with selection
     *                          10. Up with selection x 2
     */

    final int initialCaretLine = 3;
    final int initialCaretColumn = 28;
    final int initialCaretOffset = initialCaretLine * (LOREM_IPSUM.length() + 1) + initialCaretColumn;

    final var editor = initEditor(StringUtil.repeat(LOREM_IPSUM + '\n', 5) + LOREM_IPSUM, initialCaretLine, initialCaretColumn, initialCaretOffset);

    //noinspection DuplicateExpressions,PointlessArithmeticExpression
    doTestCaretNotifications(
      editor.getContentComponent(),

      // step 1
      new CaretAction(() -> leftWithSelection(),                    new CaretPos(initialCaretOffset - 1, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                    new CaretPos(initialCaretOffset - 2, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                    new CaretPos(initialCaretOffset - 3, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                    new CaretPos(initialCaretOffset - 4, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                    new CaretPos(initialCaretOffset - 5, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                    new CaretPos(initialCaretOffset - 6, initialCaretOffset)),

      // step 2
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(initialCaretOffset - 5, initialCaretOffset)),
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(initialCaretOffset - 4, initialCaretOffset)),
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(initialCaretOffset - 3, initialCaretOffset)),

      // step 3
      new CaretAction(() -> upWithSelection(),                      new CaretPos(initialCaretOffset - 3 - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 4
      new CaretAction(() -> downWithSelection(),                    new CaretPos(initialCaretOffset - 3, initialCaretOffset)),

      // step 5
      new CaretAction(() -> upWithSelection(),                      new CaretPos(initialCaretOffset - 3 - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> upWithSelection(),                      new CaretPos(initialCaretOffset - 3 - 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 6
      new CaretAction(() -> downWithSelection(),                    new CaretPos(initialCaretOffset - 3 - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> downWithSelection(),                    new CaretPos(initialCaretOffset - 3, initialCaretOffset)),
      new CaretAction(() -> downWithSelection(),                    new CaretPos(initialCaretOffset - 3 + 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> downWithSelection(),                    new CaretPos(initialCaretOffset - 3 + 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 7
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(initialCaretOffset - 2 + 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(initialCaretOffset - 1 + 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> rightWithSelection(),                   new CaretPos(initialCaretOffset     + 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 8
      new CaretAction(() -> moveCaretToNextWordWithSelection(),     new CaretPos(initialCaretOffset + 11 + 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 9
      new CaretAction(() -> moveCaretToPreviousWordWithSelection(), new CaretPos(initialCaretOffset +      2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 10
      new CaretAction(() -> upWithSelection(),                      new CaretPos(initialCaretOffset +      1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> upWithSelection(),                      new CaretPos(initialCaretOffset, initialCaretOffset))
    );
  }

  public void testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromBottomRight() {
    // the "reflection" of the testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromTopLeft

    // Bottom right
    final int initialCaretLine = 5;
    final int initialCaretColumn = LOREM_IPSUM.length();
    final int initialCaretOffset = initialCaretLine * (LOREM_IPSUM.length() + 1) + initialCaretColumn;

    final var editor = initEditor(StringUtil.repeat(LOREM_IPSUM + '\n', 5) + LOREM_IPSUM, initialCaretLine, initialCaretColumn, initialCaretOffset);

    //noinspection DuplicateExpressions,PointlessArithmeticExpression
    doTestCaretNotifications(
      editor.getContentComponent(),

      // step 1
      new CaretAction(() -> rightWithSelection(),               null),
      new CaretAction(() -> rightWithSelection(),               null),

      // step 2
      new CaretAction(() -> downWithSelection(),                null),
      new CaretAction(() -> downWithSelection(),                null),

      // step 3
      new CaretAction(() -> rightWithSelection(),               null),

      // step 4
      new CaretAction(() -> downWithSelection(),                null),

      // step 5
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 1, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 2, initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 3, initialCaretOffset)),

      // step 6
      new CaretAction(() -> upWithSelection(),                  new CaretPos(initialCaretOffset - 3 - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> upWithSelection(),                  new CaretPos(initialCaretOffset - 3 - 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 7
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 4 - 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 8
      new CaretAction(() -> upWithSelection(),                  new CaretPos(initialCaretOffset - 4 - 3 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 9
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 5 - 3 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 6 - 3 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> leftWithSelection(),                new CaretPos(initialCaretOffset - 7 - 3 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 10
      new CaretAction(() -> upWithSelection(),                  new CaretPos(initialCaretOffset - 7 - 4 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> upWithSelection(),                  new CaretPos(initialCaretOffset - 7 - 5 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 11
      new CaretAction(() -> downWithSelection(),                new CaretPos(initialCaretOffset - 7 - 4 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> downWithSelection(),                new CaretPos(initialCaretOffset - 7 - 3 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> downWithSelection(),                new CaretPos(initialCaretOffset - 7 - 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 12
      new CaretAction(() -> upWithSelection(),                  new CaretPos(initialCaretOffset - 7 - 3 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 13
      new CaretAction(() -> downWithSelection(),                new CaretPos(initialCaretOffset - 7 - 2 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> downWithSelection(),                new CaretPos(initialCaretOffset - 7 - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 14
      new CaretAction(() -> moveCaretToNextWordWithSelection(), new CaretPos(initialCaretOffset - 1 - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),
      new CaretAction(() -> rightWithSelection(),               new CaretPos(initialCaretOffset     - 1 * (LOREM_IPSUM.length() + 1), initialCaretOffset)),

      // step 15
      new CaretAction(() -> downWithSelection(),                new CaretPos(initialCaretOffset, initialCaretOffset))
    );
  }

  public void testCaretNotificationsOfSelectionMovementsWithinEmptyEditor() {
    // Similar to testCaretNotificationsWithinEmptyEditor:
    // any attempts to move the selection within an empty editor aren't supposed to cause caret movements => no caret updates are expected.

    final EditorImpl editor = initEditor("", 0, 0, 0);

    doTestCaretNotifications(
      editor.getContentComponent(),

      new CaretAction(() -> leftWithSelection(),                    null),
      new CaretAction(() -> rightWithSelection(),                   null),
      new CaretAction(() -> moveCaretToPreviousWordWithSelection(), null),
      new CaretAction(() -> upWithSelection(),                      null),
      new CaretAction(() -> moveCaretToNextWordWithSelection(),     null),
      new CaretAction(() -> downWithSelection(),                    null),
      new CaretAction(() -> leftWithSelection(),                    null),
      new CaretAction(() -> downWithSelection(),                    null),
      new CaretAction(() -> moveCaretToPreviousWordWithSelection(), null),
      new CaretAction(() -> rightWithSelection(),                   null),
      new CaretAction(() -> moveCaretToNextWordWithSelection(),     null),
      new CaretAction(() -> upWithSelection(),                      null)
    );
  }


  /* Tests for the positioning of multiple caret (no selections) */

  public void testMultiCaretNotificationsDuringTyping() {
    // Similar to testCaretNotificationsDuringTyping, but for multiple carets

    /*
     *    caret #1
     *       v
     * (0th) |Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * ...
     *                                                    caret #5
     *                                                       v
     * (2nd) Lorem ipsum dolor sit amet, consectetur adipisci|ng elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * ...
     *                     caret #4
     *                    (primary)
     *                        v
     * (4th) Lorem ipsum dolor| sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                                                                                          caret #2
     *                                                                                             v
     * (5th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incidid|unt ut labore et dolore magna aliqua.
     * ...
     *                                                                                                                               caret #3
     *                                                                                                                                  v
     * (7th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.|
     */

    final EditorImpl editor = initEditor(
      StringUtil.repeat(LOREM_IPSUM + '\n', 7) + LOREM_IPSUM,

      // caret #1
      0,
      0,
      0
    );

    // caret #2
    addCaret(editor, false, 5, 86, 5 * (LOREM_IPSUM.length() + 1) + 86);
    // caret #3
    addCaret(editor, false, 7, LOREM_IPSUM.length(), 7 * (LOREM_IPSUM.length() + 1) + LOREM_IPSUM.length());
    // caret #4 (primary)
    final int primaryCaretLine = 4;
    final int primaryCaretColumn = 17;
    final int primaryCaretInitialDot = primaryCaretLine * (LOREM_IPSUM.length() + 1) + primaryCaretColumn;
    addCaret(editor, true,  primaryCaretLine, primaryCaretColumn, primaryCaretInitialDot);
    // caret #5
    addCaret(editor, false, 2, 48, 2 * (LOREM_IPSUM.length() + 1) + 48);

    final int caretsBeforePrimaryCount = 2;

    // all notifications have to belong only to primary caret
    //noinspection PointlessArithmeticExpression
    doTestCaretNotifications(
      editor.getContentComponent(),
      new CaretAction(() -> type('u'), new CaretPos( 1 * caretsBeforePrimaryCount + primaryCaretInitialDot +  1,  1 * caretsBeforePrimaryCount + primaryCaretInitialDot +  1)),
      new CaretAction(() -> type('n'), new CaretPos( 2 * caretsBeforePrimaryCount + primaryCaretInitialDot +  2,  2 * caretsBeforePrimaryCount + primaryCaretInitialDot +  2)),
      new CaretAction(() -> type('u'), new CaretPos( 3 * caretsBeforePrimaryCount + primaryCaretInitialDot +  3,  3 * caretsBeforePrimaryCount + primaryCaretInitialDot +  3)),
      new CaretAction(() -> type('s'), new CaretPos( 4 * caretsBeforePrimaryCount + primaryCaretInitialDot +  4,  4 * caretsBeforePrimaryCount + primaryCaretInitialDot +  4)),
      new CaretAction(() -> type(' '), new CaretPos( 5 * caretsBeforePrimaryCount + primaryCaretInitialDot +  5,  5 * caretsBeforePrimaryCount + primaryCaretInitialDot +  5)),
      new CaretAction(() -> type('d'), new CaretPos( 6 * caretsBeforePrimaryCount + primaryCaretInitialDot +  6,  6 * caretsBeforePrimaryCount + primaryCaretInitialDot +  6)),
      new CaretAction(() -> type('u'), new CaretPos( 7 * caretsBeforePrimaryCount + primaryCaretInitialDot +  7,  7 * caretsBeforePrimaryCount + primaryCaretInitialDot +  7)),
      new CaretAction(() -> type('o'), new CaretPos( 8 * caretsBeforePrimaryCount + primaryCaretInitialDot +  8,  8 * caretsBeforePrimaryCount + primaryCaretInitialDot +  8)),
      new CaretAction(() -> type(' '), new CaretPos( 9 * caretsBeforePrimaryCount + primaryCaretInitialDot +  9,  9 * caretsBeforePrimaryCount + primaryCaretInitialDot +  9)),
      new CaretAction(() -> type('t'), new CaretPos(10 * caretsBeforePrimaryCount + primaryCaretInitialDot + 10, 10 * caretsBeforePrimaryCount + primaryCaretInitialDot + 10)),
      new CaretAction(() -> type('r'), new CaretPos(11 * caretsBeforePrimaryCount + primaryCaretInitialDot + 11, 11 * caretsBeforePrimaryCount + primaryCaretInitialDot + 11)),
      new CaretAction(() -> type('e'), new CaretPos(12 * caretsBeforePrimaryCount + primaryCaretInitialDot + 12, 12 * caretsBeforePrimaryCount + primaryCaretInitialDot + 12)),
      new CaretAction(() -> type('s'), new CaretPos(13 * caretsBeforePrimaryCount + primaryCaretInitialDot + 13, 13 * caretsBeforePrimaryCount + primaryCaretInitialDot + 13)),
      new CaretAction(() -> type(' '), new CaretPos(14 * caretsBeforePrimaryCount + primaryCaretInitialDot + 14, 14 * caretsBeforePrimaryCount + primaryCaretInitialDot + 14))
    );
  }


  /* Tests for the positioning of the selections of multiple carets */

  public void testMultiCaretNotificationsOfSelectionMovementsWithoutTextModifications() {
    /*
     * (0th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (1st) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                                     caret #1
     *                                        v
     * (2nd) Lorem ipsum dolor sit amet, conse|ctetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                                      caret #2
     *                                     (primary)
     *                                         v
     * (3rd) Lorem ipsum dolor sit amet, consec|tetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     *
     *                                       caret #3
     *                                          v
     * (4th) Lorem ipsum dolor sit amet, consect|etur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (5th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     * (6th) Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
     */

    final int primaryCaretLine = 3;
    final int primaryCaretColumn = 34;
    final int primaryCaretInitialDot = primaryCaretLine * (LOREM_IPSUM.length() + 1) + primaryCaretColumn;

    final EditorImpl editor = initEditor(
      StringUtil.repeat(LOREM_IPSUM + '\n', 6) + LOREM_IPSUM,

      // caret #1
      primaryCaretLine - 1,
      primaryCaretColumn - 1,
      primaryCaretInitialDot - 1 - (LOREM_IPSUM.length() + 1)
    );

    // caret #2 (primary)
    addCaret(editor, true,  primaryCaretLine, primaryCaretColumn, primaryCaretInitialDot);

    // caret #3
    addCaret(editor, false,  primaryCaretLine + 1, primaryCaretColumn + 1, primaryCaretInitialDot + 1 + (LOREM_IPSUM.length() + 1));

    //noinspection DuplicateExpressions
    doTestCaretNotifications(
      editor.getContentComponent(),

      new CaretAction(() -> leftWithSelection(),  new CaretPos(primaryCaretInitialDot - 1, primaryCaretInitialDot)),
      new CaretAction(() -> rightWithSelection(), new CaretPos(primaryCaretInitialDot,     primaryCaretInitialDot)),

      new CaretAction(() -> leftWithSelection(),  new CaretPos(primaryCaretInitialDot - 1, primaryCaretInitialDot)),
      new CaretAction(() -> leftWithSelection(),  new CaretPos(primaryCaretInitialDot - 2, primaryCaretInitialDot)),

      new CaretAction(() -> rightWithSelection(), new CaretPos(primaryCaretInitialDot - 1, primaryCaretInitialDot)),
      new CaretAction(() -> rightWithSelection(), new CaretPos(primaryCaretInitialDot,     primaryCaretInitialDot)),
      new CaretAction(() -> rightWithSelection(), new CaretPos(primaryCaretInitialDot + 1, primaryCaretInitialDot)),
      new CaretAction(() -> rightWithSelection(), new CaretPos(primaryCaretInitialDot + 2, primaryCaretInitialDot)),

      new CaretAction(() -> leftWithSelection(),  new CaretPos(primaryCaretInitialDot + 1, primaryCaretInitialDot)),
      new CaretAction(() -> leftWithSelection(),  new CaretPos(primaryCaretInitialDot,     primaryCaretInitialDot))

      // Attempts to move the carets up or down cause carets hardly predictable merging, so let's skip them
    );
  }


  /* Helpers */

  private @NotNull EditorImpl initEditor(@NotNull String text, int caretLineIndex, int caretColumnIndex, int expectedCaretPosition) {
    initText(text);

    final EditorImpl editor = (EditorImpl)getEditor();
    editor.getCaretModel().getPrimaryCaret().moveToVisualPosition(new VisualPosition(caretLineIndex, caretColumnIndex));

    assertEquals("unexpected position of dot of editor.getCaretModel().getPrimaryCaret()",
                 expectedCaretPosition, editor.getCaretModel().getPrimaryCaret().getOffset());

    // Initializing our custom a11y subsystem just to make sure that it doesn't break tests
    editor.getContentComponent().getAccessibleContext();

    return editor;
  }

  @SuppressWarnings("UnusedReturnValue")
  private static @NotNull Caret addCaret(@NotNull EditorImpl editor, boolean makePrimary, int caretLineIndex, int caretColumnIndex, int expectedCaretPosition) {
    final var result = editor.getCaretModel().addCaret(new VisualPosition(caretLineIndex, caretColumnIndex), makePrimary);
    assertNotNull("failed to add a caret", result);
    assertEquals("unexpected offset of an added caret", expectedCaretPosition, result.getOffset());
    return result;
  }

  private static void doTestCaretNotifications(@NotNull JTextComponent editorComponent, CaretAction @NotNull ... caretActions) {
    /*
     * The implementation expects the following flow:
     *     action.run() => caretUpdate() => returning control to the caller of action.run()
     */

    final Ref<@Nullable CaretPos> caretUpdateNotification = new Ref<>(null);
    final int[] i = {0}; // a reference to a mutable int

    final CaretListener listener = (event) -> {
      assertNull("a new unexpected caretUpdateNotification at CaretAction #" + i[0] + ": " + caretUpdateNotification.get(),
                 caretUpdateNotification.get());
      caretUpdateNotification.set(new CaretPos(event.getDot(), event.getMark()));
    };

    editorComponent.addCaretListener(listener);

    for (; i[0] < caretActions.length; ++i[0]) {
      caretActions[i[0]].action.run();

      final var gotCaretUpdateNotification = caretUpdateNotification.get();
      caretUpdateNotification.set(null);

      assertEquals("the wrong position(s) of the caret notification of CaretAction #" + i[0],
                   caretActions[i[0]].caretUpdateNotification, gotCaretUpdateNotification);
    }

    editorComponent.removeCaretListener(listener);

    assertNull("a new unexpected caretUpdateNotification?", caretUpdateNotification.get());
  }

  private record CaretAction(@NotNull Runnable action, @Nullable CaretPos caretUpdateNotification) {}

  private record CaretPos(int dot, int mark) {}

  private void undo() {
    executeAction(IdeActions.ACTION_UNDO);
  }
}