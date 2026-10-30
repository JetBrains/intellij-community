// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.CutProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorAccessibleTextNavigation.ExtendedTextPart;
import com.intellij.openapi.editor.impl.EditorAccessibleTextNavigation.TextPart;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
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

import static com.intellij.openapi.editor.impl.EditorAccessibleTextNavigation.AFTER;
import static com.intellij.openapi.editor.impl.EditorAccessibleTextNavigation.BEFORE;
import static com.intellij.openapi.editor.impl.EditorAccessibleTextNavigation.HERE;

final class AccessibleEditorComponentImpl extends JComponent.AccessibleJComponent
    implements AccessibleText, AccessibleEditableText, AccessibleExtendedText, AccessibleAction,
               CaretListener, DocumentListener {
  private final EditorComponentImpl component;
  private final EditorImpl editor;
  private final EditorAccessibleText myAccessibleText;
  private final EditorAccessibleTextNavigation navigation;
  private int myCaretPos;
  private int myPreviousCaretPos;

  AccessibleEditorComponentImpl(@NotNull EditorComponentImpl component) {
    component.super();
    this.component = component;
    editor = component.getEditor();
    myAccessibleText = component.getEditorAccessibleText();
    navigation = new EditorAccessibleTextNavigation(editor, myAccessibleText, () -> myCaretPos - myPreviousCaretPos >= 0);
    if (editor.isDisposed()) return;

    Disposable disposable = Disposer.newDisposable("AccessibleEditorComponentImpl");
    EditorUtil.disposeWithEditor(editor, disposable);
    editor.getCaretModel().addCaretListener(this, disposable);
    editor.getDocument().addDocumentListener(this, disposable);
    myAccessibleText.addChangeListener(this::textChanged, disposable);
  }

  // ---- Implements CaretListener ----

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
    Point nextPoint = myAccessibleText.offsetToXY(offset + 1);
    FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
    if (nextPoint.y == point.y && nextPoint.x != point.x) {
      // The layout gives the width in the font of the character, for example a bold font or a fallback font
      return new Rectangle(Math.min(point.x, nextPoint.x), point.y, Math.abs(nextPoint.x - point.x), fontMetrics.getHeight());
    }
    // The character is at a line end or before a soft wrap
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
  public @Nullable String getAtIndex(@TextPart int part, int index) {
    return navigation.getTextAtOffset(part, index, HERE);
  }

  @Override
  public @Nullable String getAfterIndex(@TextPart int part, int index) {
    return navigation.getTextAtOffset(part, index, AFTER);
  }

  @Override
  public @Nullable String getBeforeIndex(@TextPart int part, int index) {
    return navigation.getTextAtOffset(part, index, BEFORE);
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

  @Override
  public @Nullable AccessibleTextSequence getTextSequenceAt(@ExtendedTextPart int part, int index) {
    return navigation.getSequenceAtIndex(part, index, HERE);
  }

  @Override
  public @Nullable AccessibleTextSequence getTextSequenceAfter(@ExtendedTextPart int part, int index) {
    return navigation.getSequenceAtIndex(part, index, AFTER);
  }

  @Override
  public @Nullable AccessibleTextSequence getTextSequenceBefore(@ExtendedTextPart int part, int index) {
    return navigation.getSequenceAtIndex(part, index, BEFORE);
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
  public int getAccessibleActionCount() {
    return 0;
  }

  @Override
  public String getAccessibleActionDescription(int i) {
    return null;
  }

  @Override
  public boolean doAccessibleAction(int i) {
    return false;
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
