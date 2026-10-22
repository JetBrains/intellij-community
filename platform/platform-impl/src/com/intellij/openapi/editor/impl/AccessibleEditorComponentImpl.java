// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.CutProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actions.CaretStop;
import com.intellij.openapi.editor.actions.CaretStopPolicy;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleEditableText;
import javax.accessibility.AccessibleExtendedText;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.accessibility.AccessibleText;
import javax.accessibility.AccessibleTextSequence;
import javax.swing.JComponent;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;

final class AccessibleEditorComponentImpl extends JComponent.AccessibleJComponent
    implements AccessibleText, AccessibleEditableText, AccessibleExtendedText, AccessibleAction,
               CaretListener, DocumentListener {
  private final EditorComponentImpl component;
  private final EditorImpl editor;
  private final EditorAccessibleText myAccessibleText;

  AccessibleEditorComponentImpl(@NotNull EditorComponentImpl component) {
    component.super();
    this.component = component;
    editor = component.getEditor();
    myAccessibleText = component.getEditorAccessibleText();
    if (editor.isDisposed()) return;

    Disposable disposable = Disposer.newDisposable("AccessibleEditorComponentImpl");
    EditorUtil.disposeWithEditor(editor, disposable);
    editor.getCaretModel().addCaretListener(this, disposable);
    editor.getDocument().addDocumentListener(this, disposable);
    myAccessibleText.addChangeListener(this::textChanged, disposable);
  }

  // ---- Implements CaretListener ----

  private int myCaretPos;
  private int myPreviousCaretPos;


  @Override
  public void caretPositionChanged(@NotNull CaretEvent e) {
    Caret caret = e.getCaret();
    if (caret != editor.getCaretModel().getPrimaryCaret()) {
      return;
    }
    caretMoved(caret);
  }

  private void caretMoved(@NotNull Caret caret) {
    int dot = myAccessibleText.getCaretOffset(caret);
    if (myCaretPos != dot) {
      ThreadingAssertions.assertEventDispatchThread();
      firePropertyChange(ACCESSIBLE_CARET_PROPERTY,
                         Integer.valueOf(myCaretPos), Integer.valueOf(dot));
      myPreviousCaretPos = myCaretPos;
      myCaretPos = dot;
    }

    if (caret.getLeadSelectionOffset() != caret.getOffset()) {
      ThreadingAssertions.assertEventDispatchThread();
      firePropertyChange(ACCESSIBLE_SELECTION_PROPERTY, null,
                         getSelectedText());
    }
  }

  // ---- Implements DocumentListener ----

  @Override
  public void documentChanged(final @NotNull DocumentEvent event) {
    final Integer pos = myAccessibleText.fromDocumentOffset(event.getOffset());
    if (ApplicationManager.getApplication().isDispatchThread()) {
      firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, pos);
      if (SystemInfo.isMac) {
        // For MacOSX we also need to fire a JTextComponent event to anyone listening
        // to our Document, since *that* rather than the accessible property
        // change is the only way to trigger a speech update
        component.getAccessibilityDocument().fireDocumentChange(event);
      }
    } else {
      ApplicationManager.getApplication().invokeLater(() -> {
        firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, pos);
        component.getAccessibilityDocument().fireDocumentChange(event);
      });
    }
  }

  private void textChanged() {
    firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, 0);
    caretMoved(editor.getCaretModel().getPrimaryCaret());
  }

  // ---- Implements AccessibleContext ----

  @Override
  public @NotNull String getAccessibleName() {
    if (accessibleName != null) {
      return accessibleName;
    }

    VirtualFile file = editor.getVirtualFile();
    if (file != null) {
      return EditorBundle.message("editor.for.file.accessible.name", file.getName());
    }
    return EditorBundle.message("editor.accessible.name");
  }

  @Override
  public String getAccessibleDescription() {
    String description = super.getAccessibleDescription();
    if (description == null && editor.getDocument().getTextLength() == 0) {
      //noinspection HardCodedStringLiteral
      CharSequence emptyText = editor.getPlaceholder();
      if (emptyText != null && !emptyText.isEmpty()) {
        return AccessibleContextUtil.getUniqueDescription(this, emptyText.toString());
      }
    }
    return description;
  }

  @Override
  public AccessibleRole getAccessibleRole() {
    // See comment on TextAccessibleRole class.
    if (SystemInfo.isMac) {
      return TextAccessibleRole.TEXT_AREA;
    } else {
      return AccessibleRole.TEXT;
    }
  }

  @Override
  public AccessibleText getAccessibleText() {
    if (editor.isDisposed()) return null;
    return this;
  }

  @Override
  public AccessibleEditableText getAccessibleEditableText() {
    if (editor.isDisposed()) return null;
    return this;
  }

  @Override
  public AccessibleStateSet getAccessibleStateSet() {
    AccessibleStateSet states = super.getAccessibleStateSet();
    if (editor.getDocument().isWritable()) {
      states.add(AccessibleState.EDITABLE);
    }
    states.add(AccessibleState.MULTI_LINE);
    return states;
  }

  // ---- Implements AccessibleText ----

  @Override
  public int getIndexAtPoint(Point point) {
    return myAccessibleText.getOffsetAt(point);
  }

  @Override
  public Rectangle getCharacterBounds(int offset) {
    // Since we report the very end of the document as being 1 character past the document
    // length, we need to validate the offset passed back by the screen reader.
    if (offset < 0 || offset > myAccessibleText.getLength() - 1) {
      return null;
    }
    Point point = myAccessibleText.offsetToXY(offset);
    FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
    char c = myAccessibleText.getText(offset, offset + 1).charAt(0);
    return new Rectangle(point.x, point.y, fontMetrics.charWidth(c), fontMetrics.getHeight());
  }

  @Override
  public int getCharCount() {
    return myAccessibleText.getLength();
  }

  @Override
  public int getCaretPosition() {
    return component.getCaretPosition();
  }

  @Override
  public @Nullable String getAtIndex(
    @MagicConstant(intValues = {
      CHARACTER,
      WORD,
      SENTENCE})
    int part,
    int index) {
    return getTextAtOffset(part, index, HERE);
  }

  @Override
  public @Nullable String getAfterIndex(
    @MagicConstant(intValues = {CHARACTER, WORD, SENTENCE})
    int part,
    int index) {
    return getTextAtOffset(part, index, AFTER);
  }

  @Override
  public @Nullable String getBeforeIndex(
    @MagicConstant(intValues = {CHARACTER, WORD, SENTENCE})
    int part,
    int index) {
    return getTextAtOffset(part, index, BEFORE);
  }

  @Override
  public AttributeSet getCharacterAttribute(int index) {
    return new SimpleAttributeSet();
  }

  @Override
  public int getSelectionStart() {
    return EditorThreading.compute(() -> myAccessibleText.getSelectionStart());
  }

  @Override
  public int getSelectionEnd() {
    return EditorThreading.compute(() -> myAccessibleText.getSelectionEnd());
  }

  @Override
  public @Nullable String getSelectedText() {
    return EditorThreading.compute(() -> myAccessibleText.getSelectedText());
  }

  // ---- Implements AccessibleEditableText ----

  @Override
  public void setTextContents(String s) {
    component.setText(s);
  }

  @Override
  public void insertTextAtIndex(int index, String s) {
    EditorComponentImpl.editDocumentSafely(editor, myAccessibleText.toDocumentOffset(index), 0, s);
  }

  @Override
  public String getTextRange(int startIndex, int endIndex) {
    return myAccessibleText.getText(startIndex, endIndex);
  }

  @Override
  public void delete(int startIndex, int endIndex) {
    TextRange range = myAccessibleText.toDocumentRange(startIndex, endIndex);
    EditorComponentImpl.editDocumentSafely(editor, range.getStartOffset(), range.getLength(), null);
  }

  @Override
  public void cut(int startIndex, int endIndex) {
    TextRange range = myAccessibleText.toDocumentRange(startIndex, endIndex);
    editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    DataContext dataContext = DataManager.getInstance().getDataContext(component);
    CutProvider cutProvider = editor.getCutProvider();
    if (cutProvider.isCutEnabled(dataContext)) {
      cutProvider.performCut(dataContext);
    }
  }

  @Override
  public void paste(int startIndex) {
    editor.getCaretModel().moveToOffset(myAccessibleText.toDocumentOffset(startIndex));
    DataContext dataContext = DataManager.getInstance().getDataContext(component);
    PasteProvider pasteProvider = editor.getPasteProvider();
    if (pasteProvider.isPasteEnabled(dataContext)) {
      pasteProvider.performPaste(dataContext);
    }
  }

  @Override
  public void replaceText(int startIndex, int endIndex, String s) {
    TextRange range = myAccessibleText.toDocumentRange(startIndex, endIndex);
    EditorComponentImpl.editDocumentSafely(editor, range.getStartOffset(), range.getLength(), s);
  }

  @Override
  public void selectText(int startIndex, int endIndex) {
    EditorThreading.run(() -> {
      // a caret set inside a placeholder stays there, so that the caret position that a screen reader sets reads back the same
      VisualPosition placeholderPosition = startIndex == endIndex ? myAccessibleText.getPlaceholderVisualPosition(startIndex) : null;
      if (placeholderPosition != null) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToVisualPosition(placeholderPosition);
        return;
      }
      TextRange range = myAccessibleText.toDocumentRange(startIndex, endIndex);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      editor.getCaretModel().moveToOffset(range.getEndOffset());
    });
  }

  @Override
  public void setAttributes(int startIndex, int endIndex, AttributeSet as) {
  }

  // ---- Implements AccessibleExtendedText ----

  /** Looking for text before the given offset */
  private static final int BEFORE = -1;
  /** Looking for text at the given offset */
  private static final int HERE = 0;
  /** Looking for text after the given offset */
  private static final int AFTER = 1;

  @Override
  public @Nullable AccessibleTextSequence getTextSequenceAt(
    @MagicConstant(intValues = {
      CHARACTER,
      WORD,
      SENTENCE,
      LINE,
      ATTRIBUTE_RUN})
    int part,
    int index) {
    return getSequenceAtIndex(part, index, HERE);
  }

  @Override
  public @Nullable AccessibleTextSequence getTextSequenceAfter(
    @MagicConstant(intValues = {
      CHARACTER,
      WORD,
      SENTENCE,
      LINE,
      ATTRIBUTE_RUN})
    int part,
    int index) {
    return getSequenceAtIndex(part, index, AFTER);
  }

  @Override
  public @Nullable AccessibleTextSequence getTextSequenceBefore(
    @MagicConstant(intValues = {
      CHARACTER,
      WORD,
      SENTENCE,
      LINE,
      ATTRIBUTE_RUN})
    int part,
    int index) {
    return getSequenceAtIndex(part, index, BEFORE);
  }

  @Override
  public @NotNull Rectangle getTextBounds(int startIndex, int endIndex) {
    Point startPoint = myAccessibleText.offsetToXY(startIndex);
    Rectangle rectangle = new Rectangle(startPoint);

    Point endPoint = myAccessibleText.offsetToXY(endIndex);
    FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
    char c = myAccessibleText.getText(endIndex - 1, endIndex).charAt(0);
    endPoint.x += fontMetrics.charWidth(c);
    endPoint.y += fontMetrics.getHeight();
    rectangle.add(endPoint);

    return rectangle;
  }

  // ---- Implements AccessibleAction ----

  @Override
  public int getAccessibleActionCount(){
    return 0;
  }

  @Override
  public String getAccessibleActionDescription(int i){
    return null;
  }

  @Override
  public boolean doAccessibleAction(int i) {
    return false;
  }

  private @Nullable String getTextAtOffset(
    @MagicConstant(intValues = {CHARACTER, WORD, SENTENCE})
    int type,
    int offset,
    @MagicConstant(intValues = {BEFORE, HERE, AFTER})
    int direction) {
    int length = myAccessibleText.getLength();
    if (offset < 0 || offset >= length) {
      return null;
    }
    switch (type) {
      case CHARACTER: {
        if (offset + direction < length && offset + direction >= 0) {
          int startOffset = offset + direction;
          return myAccessibleText.getText(startOffset, startOffset + 1);
        }
        break;
      }

      case WORD: {
        var word = getWordOrLexeme(offset, direction);
        return word == null ? null : word.text;
      }

      case SENTENCE: {
        int lineStart = getLineAtOffsetStart(offset, direction);
        int lineEnd = getLineAtOffsetEnd(offset, direction);
        if (lineStart == -1 || lineEnd == -1) {
          return null;
        }
        return myAccessibleText.getText(lineStart, lineEnd);
      }

      case LINE:
      case ATTRIBUTE_RUN:
        // Not expected to be called in this method!
        assert false : type;

      default:
        break;
    }

    return null;
  }

  /**
   * Similar to {@link #getTextAtOffset} but returns an {@link AccessibleTextSequence} and can accept a few more types.
   */
  private @Nullable AccessibleTextSequence getSequenceAtIndex(
      @MagicConstant(intValues = {
        CHARACTER,
        WORD,
        SENTENCE,
        LINE,
        ATTRIBUTE_RUN})
      int type,
      int offset,
      @MagicConstant(intValues = {BEFORE, HERE, AFTER})
      int direction) {
    assert direction == BEFORE || direction == HERE || direction == AFTER;

    int length = myAccessibleText.getLength();
    if (offset < 0 || offset >= length) {
      return null;
    }

    switch (type) {
      case CHARACTER -> {
        AccessibleTextSequence charSequence = null;
        if (offset + direction < length &&
            offset + direction >= 0) {
          int startOffset = offset + direction;
          charSequence = new AccessibleTextSequence(startOffset, startOffset + 1,
                                                    myAccessibleText.getText(startOffset, startOffset + 1));
        }
        return charSequence;
      }
      case ATTRIBUTE_RUN, WORD -> {
        return getWordOrLexeme(offset, direction);
      }
      case LINE, SENTENCE -> {
        int lineStart = getLineAtOffsetStart(offset, direction);
        int lineEnd = getLineAtOffsetEnd(offset, direction);
        if (lineStart == -1 || lineEnd == -1) {
          return null;
        }

        return new AccessibleTextSequence(lineStart, lineEnd, myAccessibleText.getText(lineStart, lineEnd));
      }
    }
    return null;
  }

  private int getLineAtOffsetStart(int offset) {
    Document document = editor.getDocument();
    if (offset == 0) {
      return 0;
    }
    int lineNumber = editor.offsetToLogicalPosition(offset).line;
    return document.getLineStartOffset(lineNumber);
  }

  private int moveLineOffset(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    if (direction == AFTER) {
      int lineNumber = myAccessibleText.getLineNumber(offset);
      lineNumber++;
      if (lineNumber == myAccessibleText.getLineCount()) {
        return -1;
      }
      return myAccessibleText.getLineStartOffset(lineNumber);
    } else if (direction == BEFORE) {
      int lineNumber = myAccessibleText.getLineNumber(offset);
      lineNumber--;
      if (lineNumber < 0) {
        return -1;
      }
      return myAccessibleText.getLineStartOffset(lineNumber);
    } else {
      assert direction == HERE;
      return offset;
    }
  }

  private int getLineAtOffsetStart(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    offset = moveLineOffset(offset, direction);
    if (offset == -1) {
      return -1;
    }

    return myAccessibleText.getLineStartOffset(myAccessibleText.getLineNumber(offset));
  }

  private int getLineAtOffsetEnd(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    offset = moveLineOffset(offset, direction);
    if (offset == -1) {
      return -1;
    }

    return myAccessibleText.getLineEndOffset(myAccessibleText.getLineNumber(offset));
  }

  private CaretStopPolicy resolveCaretStopPolicy(@MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    var caretStopOptions = editor.getSettings().getCaretStopOptions();
    switch (direction) {
      case AFTER -> {
        return caretStopOptions.getForwardPolicy();
      }
      case BEFORE -> {
        return caretStopOptions.getBackwardPolicy();
      }
      case HERE -> {
        return myCaretPos - myPreviousCaretPos >= 0 ? caretStopOptions.getForwardPolicy() : caretStopOptions.getBackwardPolicy();
      }
    }
    return caretStopOptions.getForwardPolicy();
  }

  private AccessibleTextSequence getWordOrLexeme(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    if (direction == HERE) {
      TextRange placeholder = myAccessibleText.getPlaceholderRange(offset);
      if (placeholder != null) {
        int start = placeholder.getStartOffset();
        int end = placeholder.getEndOffset();
        return new AccessibleTextSequence(start, end, myAccessibleText.getText(start, end));
      }
    }
    // the word helpers below use document offsets
    boolean isCamel = editor.getSettings().isCamelWords();
    var caretStopPolicy = resolveCaretStopPolicy(direction);
    offset = moveWordOffset(myAccessibleText.toDocumentOffset(offset), direction, caretStopPolicy, isCamel);
    var wordStop = caretStopPolicy.getWordStop();
    int wordStart = getWordAtOffsetStart(offset, wordStop, isCamel);
    int wordEnd = getWordAtOffsetEnd(offset, wordStop, isCamel);
    if (wordStart == -1 || wordEnd == -1 || wordStart > wordEnd) {
      return null;
    }
    int start = myAccessibleText.fromDocumentOffset(wordStart);
    int end = myAccessibleText.fromDocumentOffset(wordEnd);
    return new AccessibleTextSequence(start, end, myAccessibleText.getText(start, end));
  }


  private int moveWordOffset(int offset,
                             @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction,
                             CaretStopPolicy stopPolicy,
                             boolean isCamel) {
    if (direction == AFTER) {
      return EditorActionUtil.getNextCaretStopOffset(editor, stopPolicy, isCamel);
    }
    if (direction == BEFORE) {
      return EditorActionUtil.getPreviousCaretStopOffset(editor, stopPolicy, isCamel);
    }
    return offset;
  }

  private int getWordAtOffsetStart(int offset, CaretStop wordStop, boolean isCamel) {
    if (offset == 0) {
      return 0;
    }
    if (wordStop.isAtEnd() && !wordStop.isAtStart()) {
      if (EditorActionUtil.isWordOrLexemeEnd(editor, offset, isCamel)) {
        return getWordAtOffsetStart(offset - 1, isCamel);
      }
      if (offset == getLineAtOffsetStart(offset)) {
        return offset - 1;
      }
    }
    if (Character.isWhitespace(editor.getDocument().getText().charAt(offset))) {
      return offset;
    }
    return getWordAtOffsetStart(offset, isCamel);
  }

  // Based on CaretImpl#getWordAtCaretStart
  private int getWordAtOffsetStart(int offset, boolean isCamel) {
    Document document = editor.getDocument();
    if (offset == 0) {
      return 0;
    }
    int lineNumber = editor.offsetToLogicalPosition(offset).line;
    int newOffset = offset;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordOrLexemeStart(editor, newOffset, isCamel)) {
        break;
      }
    }

    return newOffset;
  }

  private int getWordAtOffsetEnd(int offset, CaretStop stopWord, boolean isCamel) {
    if (stopWord.isAtStart()) {
      if (Character.isWhitespace(editor.getDocument().getText().charAt(offset))) {
        return offset + 1;
      }
      return getWordAtOffsetEnd(offset + 1, isCamel);
    }
    if (offset == getLineAtOffsetStart(offset)) {
      return offset;
    }
    return getWordAtOffsetEnd(offset, isCamel);
  }


  // Based on CaretImpl#getWordAtCaretEnd
  private int getWordAtOffsetEnd(int offset, boolean isCamel) {
    Document document = editor.getDocument();
    if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) {
      return offset;
    }
    int newOffset = offset;
    int lineNumber = editor.offsetToLogicalPosition(offset).line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) {
        return offset;
      }
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordOrLexemeEnd(editor, newOffset, isCamel)) {
        break;
      }
    }

    return newOffset;
  }

  private static final class TextAccessibleRole extends AccessibleRole {
    // Can't use AccessibleRole.TEXT: The screen reader verbally refers to it as a text field
    // and doesn't do multi-line iteration. (This is hardcoded into the sun/lwawt/macosx implementation.)
    // As you can see from JavaAccessibilityUtilities.m, we should use the exact key "textarea" to get
    // proper text area handling.
    // Note: This is true for MacOS only. For other platform, we need to return the "regular"
    // TEXT role to ensure screen readers behave as expected.
    private static final AccessibleRole TEXT_AREA = new TextAccessibleRole("textarea");

    private TextAccessibleRole(@NonNls String key) {
      super(key);
    }
  }
}
