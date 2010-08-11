/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.ActionCallback;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.InputMethodEvent;
import java.awt.im.InputMethodRequests;
import java.util.Map;

/**
 *
 */
public class EditorComponentImpl extends JComponent implements Scrollable, DataProvider, Queryable, TypingTarget {
  private final EditorImpl myEditor;

  public EditorComponentImpl(EditorImpl editor) {
    myEditor = editor;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);
    setFocusCycleRoot(true);
    setOpaque(true);
  }

  public EditorImpl getEditor() {
    return myEditor;
  }


  public Object getData(String dataId) {
    if (myEditor.isRendererMode()) return null;

    if (PlatformDataKeys.EDITOR.is(dataId)) {
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

  public Dimension getPreferredSize() {
    return myEditor.getPreferredSize();
  }

  protected void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  protected void processInputMethodEvent(InputMethodEvent e) {
    super.processInputMethodEvent(e);
    if (!e.isConsumed()) {
      switch (e.getID()) {
        case InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
          myEditor.replaceInputMethodText(e);
          // No breaks over here.

        case InputMethodEvent.CARET_POSITION_CHANGED:
          myEditor.inputMethodCaretPositionChanged(e);
          break;
      }
      e.consume();
    }
  }

  public ActionCallback type(String text) {
    return myEditor.type(text);
  }

  public InputMethodRequests getInputMethodRequests() {
    return myEditor.getInputMethodRequests();
  }

  public void paintComponent(Graphics g) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

    try {
      ((Graphics2D)g).setComposite(AlphaComposite.Src);

      UISettings.setupAntialiasing(g);
      myEditor.paint(g);
    }
    finally {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
    }
  }

  public void repaintEditorComponent() {
    repaint();
  }

  public void repaintEditorComponent(int x, int y, int width, int height) {
    repaint(x, y, width, height);
  }

  //--implementation of Scrollable interface--------------------------------------
  public Dimension getPreferredScrollableViewportSize() {
    return myEditor.getPreferredSize();
  }

  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      return myEditor.getLineHeight();
    }
    else { // if orientation == SwingConstants.HORIZONTAL
      return EditorUtil.getSpaceWidth(Font.PLAIN, myEditor);
    }
  }

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
    else { // if orientation == SwingConstants.HORIZONTAL
      return visibleRect.width;
    }
  }

  public boolean getScrollableTracksViewportWidth() {
    return getParent()instanceof JViewport && getParent().getWidth() > getPreferredSize().width;
  }

  public boolean getScrollableTracksViewportHeight() {
    return getParent()instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
  }

  public void putInfo(Map<String, String> info) {
    myEditor.putInfo(info);
  }
}
