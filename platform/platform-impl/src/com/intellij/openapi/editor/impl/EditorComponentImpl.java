/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.ide.CutProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Grayer;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputMethodEvent;
import java.awt.im.InputMethodRequests;
import java.util.Map;

public class EditorComponentImpl extends JComponent implements Scrollable, DataProvider, Queryable, TypingTarget, Accessible {
  private final EditorImpl myEditor;
  private final ApplicationImpl myApplication;

  public EditorComponentImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);
    setFocusCycleRoot(true);
    setOpaque(true);

    putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, new Magnificator() {
      @Override
      public Point magnify(double scale, Point at) {
        VisualPosition magnificationPosition = myEditor.xyToVisualPosition(at);
        double currentSize = myEditor.getColorsScheme().getEditorFontSize();
        int defaultFontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
        myEditor.setFontSize(Math.max((int)(currentSize * scale), defaultFontSize));

        return myEditor.visualPositionToXY(magnificationPosition);
      }
    });
    myApplication = (ApplicationImpl)ApplicationManager.getApplication();
  }

  @Override
  public void paint(@NotNull Graphics g) {
    if (!isEnabled()) {
      g = new Grayer((Graphics2D)g, EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    }
    super.paint(g);
  }

  @NotNull
  public EditorImpl getEditor() {
    return myEditor;
  }

  @Override
  public Object getData(String dataId) {
    if (myEditor.isRendererMode()) return null;

    // but for server edition return current editor
    // TODO replace
    if(ApplicationManager.getApplication().isServer()) {
      if (CommonDataKeys.EDITOR.is(dataId)) {
        return myEditor;
      }
      if(CommonDataKeys.PROJECT.is(dataId)) {
        return myEditor.getProject();
      }
    }

    if (CommonDataKeys.EDITOR.is(dataId)) {
      // for 'big' editors return null to allow injected editors (see com.intellij.openapi.fileEditor.impl.text.TextEditorComponent.getData())
      return myEditor.getVirtualFile() == null ? myEditor : null;
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myEditor.getDeleteProvider();
    }
    if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return myEditor.getCutProvider();
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myEditor.getCopyProvider();
    }
    if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myEditor.getPasteProvider();
    }

    return null;
  }

  @Override
  public Color getBackground() {
    return myEditor.getBackgroundColor();
  }

  @Override
  public Dimension getPreferredSize() {
    return myEditor.getPreferredSize();
  }

  protected void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  protected void processInputMethodEvent(InputMethodEvent e) {
    super.processInputMethodEvent(e);
    if (!e.isConsumed()) {
      switch (e.getID()) {
        case InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
          myEditor.replaceInputMethodText(e);
          // No breaks over here.

          //noinspection fallthrough
        case InputMethodEvent.CARET_POSITION_CHANGED:
          myEditor.inputMethodCaretPositionChanged(e);
          break;
      }
      e.consume();
    }
  }

  @Override
  public ActionCallback type(final String text) {
    final ActionCallback result = new ActionCallback();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myEditor.type(text).notify(result);
      }
    });
    return result;
  }

  @Override
  public InputMethodRequests getInputMethodRequests() {
    return IdeEventQueue.getInstance().isInputMethodEnabled() ? myEditor.getInputMethodRequests() : null;
  }

  @Override
  public void paintComponent(Graphics g) {
    myApplication.editorPaintStart();

    try {
      Graphics2D gg = !Boolean.TRUE.equals(EditorTextField.SUPPLEMENTARY_KEY.get(myEditor)) ?
                      IdeBackgroundUtil.withEditorBackground(g, this) : (Graphics2D)g;
      UIUtil.setupComposite(gg);
      EditorUIUtil.setupAntialiasing(gg);
      myEditor.paint(gg);
    }
    finally {
      myApplication.editorPaintFinish();
    }
  }

  @Override
  public void revalidate() {
    myEditor.resetPaintersWidth();
    super.revalidate();
  }

  public void repaintEditorComponent() {
    repaint();
  }

  public void repaintEditorComponent(int x, int y, int width, int height) {
    repaint(x, y, width, height);
  }

  //--implementation of Scrollable interface--------------------------------------
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return myEditor.getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      return myEditor.getLineHeight();
    }
    // if orientation == SwingConstants.HORIZONTAL
    return EditorUtil.getSpaceWidth(Font.PLAIN, myEditor);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int lineHeight = myEditor.getLineHeight();
      if (direction > 0) {
        int lineNumber = (visibleRect.y + visibleRect.height) / lineHeight;
        return lineHeight * lineNumber - visibleRect.y;
      }
      else {
        int lineNumber = (visibleRect.y - visibleRect.height) / lineHeight;
        return visibleRect.y - lineHeight * lineNumber;
      }
    }
    // if orientation == SwingConstants.HORIZONTAL
    return visibleRect.width;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return getParent() instanceof JViewport && getParent().getWidth() > getPreferredSize().width;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    myEditor.putInfo(info);
  }

  @NonNls
  @Override
  public String toString() {
    return "EditorComponent file=" + myEditor.getVirtualFile();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null && !myEditor.isDisposed()) {
        accessibleContext = new AccessibleEditor();
    }
    return accessibleContext;
  }

  /**
   * @author Konstantin Bulenkov
   */
  protected class AccessibleEditor extends AccessibleJComponent implements AccessibleText, AccessibleAction, AccessibleEditableText,
                                                                           AccessibleExtendedText, CaretListener, DocumentListener {

    int caretPos;
    Point oldLocationOnScreen;

    public AccessibleEditor() {
      getEditor().getDocument().addDocumentListener(this, myEditor.getDisposable());
      getEditor().getCaretModel().addCaretListener(this);
      caretPos = getCaretPosition();

      try {
        oldLocationOnScreen = getLocationOnScreen();
      }
      catch (IllegalComponentStateException iae) {//
      }

      EditorComponentImpl.this.addComponentListener(new ComponentAdapter() {

        public void componentMoved(ComponentEvent e) {
          try {
            Point newLocationOnScreen = getLocationOnScreen();
            firePropertyChange(ACCESSIBLE_VISIBLE_DATA_PROPERTY, oldLocationOnScreen, newLocationOnScreen);
            oldLocationOnScreen = newLocationOnScreen;
          }
          catch (IllegalComponentStateException iae) {//
          }
        }
      });
    }

    public void caretUpdate(CaretEvent e) {
      //int dot = e.getDot();
      //int mark = e.getMark();
      //if (caretPos != dot) {
      //  // the caret moved
      //  firePropertyChange(ACCESSIBLE_CARET_PROPERTY, new Integer(caretPos), new Integer(dot));
      //  caretPos = dot;
      //
      //  try {
      //    oldLocationOnScreen = getLocationOnScreen();
      //  }
      //  catch (IllegalComponentStateException iae) {//
      //  }
      //}
      //if (mark != dot) {
      //  // there is a selection
      //  firePropertyChange(ACCESSIBLE_SELECTION_PROPERTY, null, getSelectedText());
      //}
    }


    public void insertUpdate(DocumentEvent e) {
      documentUpdated(e.getOffset());
    }

    protected void documentUpdated(int index) {
      final Integer offset = new Integer(index);
      if (SwingUtilities.isEventDispatchThread()) {
        firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, offset);
      }
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, offset);
          }
        });
      }
    }

    public void removeUpdate(DocumentEvent e) {
      documentUpdated(e.getOffset());
    }

    public void changedUpdate(DocumentEvent e) {
      documentUpdated(e.getOffset());
    }


    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet states = super.getAccessibleStateSet();
      if (!EditorComponentImpl.this.getEditor().isViewer()) {
        states.add(AccessibleState.EDITABLE);
      }
      if (!getEditor().isOneLineMode()) {
        states.add(AccessibleState.MULTI_LINE);
      }
      return states;
    }

    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.TEXT;
    }

    public AccessibleText getAccessibleText() {
      return this;
    }

    public int getIndexAtPoint(Point p) {
      if (p == null) {
        return -1;
      }
      final EditorImpl editor = EditorComponentImpl.this.getEditor();
      return editor.logicalPositionToOffset(editor.xyToLogicalPosition(p));
    }

    public Rectangle getCharacterBounds(int i) {
      final EditorImpl editor = EditorComponentImpl.this.getEditor();
      final Point point = editor.offsetToXY(i, true);
      //todo[kb] more accurate calculation here
      final int width = editor.getFontMetrics(Font.PLAIN).charWidth(editor.getDocument().getText().charAt(i));
      return new Rectangle(point, new Dimension(width, editor.getLineHeight()));
    }

    public int getCharCount() {
      return getEditor().getDocument().getTextLength();
    }


    public int getCaretPosition() {
      return getEditor().getCaretModel().getOffset();
    }

    public AttributeSet getCharacterAttribute(int i) {
      return null; //todo[kb]
    }

    public int getSelectionStart() {
      return getEditor().getSelectionModel().getSelectionStart();
    }

    public int getSelectionEnd() {
      return getEditor().getSelectionModel().getSelectionEnd();
    }

    public String getSelectedText() {
      return getEditor().getSelectionModel().getSelectedText();
    }

    @Override
    public void caretPositionChanged(CaretEvent e) {
      caretUpdate(e);
    }

    @Override
    public void caretAdded(CaretEvent e) {
      caretUpdate(e);
    }

    @Override
    public void caretRemoved(CaretEvent e) {
      caretUpdate(e);
    }

    @Override
    public void beforeDocumentChange(com.intellij.openapi.editor.event.DocumentEvent event) {

    }

    @Override
    public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
      documentUpdated(event.getOffset());
    }

    public String getAtIndex(int part, int index) {
      return getAtIndex(part, index, 0);
    }

    public String getAfterIndex(int part, int index) {
      return getAtIndex(part, index, 1);
    }

    public String getBeforeIndex(int part, int index) {
      return getAtIndex(part, index, -1);
    }

    private String getAtIndex(int part, int index, int direction) {
      return "?"; //todo
    }

    public AccessibleEditableText getAccessibleEditableText() {
      return this;
    }

    public void setTextContents(String s) {
      getEditor().getDocument().setText(s);
    }


    public void insertTextAtIndex(int index, String s) {
      getEditor().getDocument().insertString(index, s);
    }

    public String getTextRange(int startIndex, int endIndex) {
      return getEditor().getDocument().getText(TextRange.create(startIndex, endIndex));
    }

    public void delete(int startIndex, int endIndex) {
      if (!getEditor().isViewer()) {
        getEditor().getDocument().deleteString(startIndex, endIndex);
      }
      else {
        UIManager.getLookAndFeel().provideErrorFeedback(EditorComponentImpl.this);
      }
    }

    public void cut(int startIndex, int endIndex) {
      selectText(startIndex, endIndex);
      final DataContext context = DataManager.getInstance().getDataContext(EditorComponentImpl.this);
      final CutProvider cutProvider = getEditor().getCutProvider();
      if (cutProvider.isCutEnabled(context)) {
        cutProvider.performCut(context);
      }
    }

    public void paste(int startIndex) {
      getEditor().getCaretModel().moveToOffset(startIndex);
      final PasteProvider pasteProvider = getEditor().getPasteProvider();
      final DataContext context = DataManager.getInstance().getDataContext(EditorComponentImpl.this);
      if (pasteProvider.isPasteEnabled(context)) {
        pasteProvider.performPaste(context);
      }
    }

    public void replaceText(int startIndex, int endIndex, String s) {
      getEditor().getDocument().replaceString(startIndex, endIndex, s);
    }

    public void selectText(int startIndex, int endIndex) {
      getEditor().getSelectionModel().setSelection(startIndex, endIndex);
    }

    public void setAttributes(int startIndex, int endIndex, AttributeSet as) {
      //todo[kb] not supported?
    }

    private AccessibleTextSequence getSequenceAtIndex(int part, int index, int direction) {
      //todo[kb]
      return null;
    }


    public AccessibleTextSequence getTextSequenceAt(int part, int index) {
      return getSequenceAtIndex(part, index, 0);
    }

    public AccessibleTextSequence getTextSequenceAfter(int part, int index) {
      return getSequenceAtIndex(part, index, 1);
    }

    public AccessibleTextSequence getTextSequenceBefore(int part, int index) {
      return getSequenceAtIndex(part, index, -1);
    }

    public Rectangle getTextBounds(int startIndex, int endIndex) {
      final Point start = getEditor().offsetToXY(startIndex, true);
      final Point end = getEditor().offsetToXY(endIndex, true);
      if (start.y == end.y) {
        return new Rectangle(start, new Dimension(end.x - start.x, getEditor().getLineHeight()));
      }
      final int width = getEditor().getMaxWidthInRange(startIndex, endIndex);
      return new Rectangle(start, new Dimension(width, end.y - start.y + getEditor().getLineHeight()));
    }

    public AccessibleAction getAccessibleAction() {
      return this;
    }

    public int getAccessibleActionCount() {
      return 0;
    }

    public String getAccessibleActionDescription(int i) {
      return null;
    }

    public boolean doAccessibleAction(int i) {
      return false;
    }
  }
}
