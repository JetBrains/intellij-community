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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.LabeledEditor;
import com.intellij.openapi.diff.impl.util.SyncScrollSupport;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class DiffSideView {
  private final JComponent MOCK_COMPONENT = new JPanel();
  {
    MOCK_COMPONENT.setFocusable(true);
  }

  private static final DiffHighlighterFactory DUMMY_HIGHLIGHTER_FACTORY = new DiffHighlighterFactoryImpl(null, null, null);
  private final LabeledEditor myPanel = new LabeledEditor();

  private final DiffSidesContainer myContainer;
  private final CurrentLineMarker myLineMarker = new CurrentLineMarker();

  private DiffHighlighterFactory myHighlighterFactory = DUMMY_HIGHLIGHTER_FACTORY;
  private EditorSource myEditorSource = EditorSource.NULL;
  private boolean myIsMaster = false;
  private String myTitle;

  public DiffSideView(String title, DiffSidesContainer container) {
    myTitle = title;
    myContainer = container;
    insertComponent(MOCK_COMPONENT);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setEditorSource(EditorSource source) {
    MyState state = new MyState();
    myEditorSource = source;
    myLineMarker.attach(myEditorSource);
    Editor editor = myEditorSource.getEditor();
    if (editor == null) {
      insertComponent(MOCK_COMPONENT);
      return;
    }
    editor.getScrollingModel().scrollHorizontally(0);
    insertComponent(editor.getComponent());
    applyHighlighter();
    setMouseListeners(source);
    MyEditorFocusListener.install(this);

    state.restore();
  }

  private void insertComponent(JComponent component) {
    myPanel.setComponent(component, myTitle);
  }

  public void setHighlighterFactory(DiffHighlighterFactory highlighterFactory) {
    myHighlighterFactory = highlighterFactory;
    applyHighlighter();
  }

  private void applyHighlighter() {
    EditorEx editor = myEditorSource.getEditor();
    if (editor == null) return;
    EditorHighlighter highlighter = myHighlighterFactory.createHighlighter();
    if (highlighter != null) editor.setHighlighter(highlighter);
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);
  }

  public void setTitle(String title) {
    myTitle = title;
    Editor editor = getEditor();
    if (editor == null) return;
    boolean readonly = editor.isViewer() || !editor.getDocument().isWritable();
    myPanel.updateTitle(myTitle, readonly);
  }

  private void setMouseListeners(EditorSource source) {
    DiffContent content = source.getContent();
    MouseLineNumberListener.install(content, source, myContainer);
  }

  public void beSlave() {
    myIsMaster = false;
    myLineMarker.hide();
  }

  private static class MouseLineNumberListener {
    private static final Cursor HAND__CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    private final Editor myEditor;
    private final DiffSidesContainer myContainer;
    private final DiffContent myContent;
    private final Project myProject;

    private final EditorMouseAdapter myMouseListener = new EditorMouseAdapter() {
      public void mouseReleased(EditorMouseEvent e) {
        if (!isInMyArea(e)) return;
        OpenFileDescriptor descriptor = getOpenFileDescriptor(e);
        if (descriptor == null) return;
        myContainer.showSource(descriptor);
      }
    };

    private OpenFileDescriptor getOpenFileDescriptor(EditorMouseEvent e) {
      int offset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
      return myContent.getOpenFileDescriptor(offset);
    }

    private boolean isInMyArea(EditorMouseEvent e) {
      return e.getArea() == EditorMouseEventArea.LINE_NUMBERS_AREA;
    }

    private final EditorMouseMotionAdapter myMouseMotionListener = new EditorMouseMotionAdapter() {
      public void mouseMoved(EditorMouseEvent e) {
        Editor editor = e.getEditor();
        if (editor.getProject() != null && editor.getProject() != myProject && myProject != null/*???*/) return;
        if (!isInMyArea(e)) return;
        Cursor cursor = getOpenFileDescriptor(e) != null ? HAND__CURSOR : Cursor.getDefaultCursor();
        e.getMouseEvent().getComponent().setCursor(cursor);
        myEditor.getContentComponent().setCursor(cursor);
      }
    };

    public MouseLineNumberListener(DiffContent content, Editor editor, DiffSidesContainer container, Project project) {
      myEditor = editor;
      myContainer = container;
      myContent = content;
      myProject = project;
    }

    public static void install(DiffContent content, EditorSource source, DiffSidesContainer container) {
      final Editor editor = source.getEditor();
      Project project = container.getProject();
      if (project == null) return;
      final MouseLineNumberListener listener = new MouseLineNumberListener(content, editor, container, project);
      editor.addEditorMouseListener(listener.myMouseListener);
      editor.addEditorMouseMotionListener(listener.myMouseMotionListener);
      source.addDisposable(new Disposable() {
        public void dispose() {
          editor.removeEditorMouseListener(listener.myMouseListener);
          editor.removeEditorMouseMotionListener(listener.myMouseMotionListener);
        }
      });
    }

  }

  private static class MyEditorFocusListener extends FocusAdapter {
    private final DiffSideView mySideView;

    private MyEditorFocusListener(DiffSideView sideView) {
      mySideView = sideView;
    }

    public void focusGained(FocusEvent e) {
      mySideView.becomeMaster();
    }

    public static MyEditorFocusListener install(DiffSideView sideView) {
      final MyEditorFocusListener listener = new MyEditorFocusListener(sideView);
      final JComponent focusableComponent = sideView.getFocusableComponent();
      focusableComponent.addFocusListener(listener);
      sideView.myEditorSource.addDisposable(new Disposable() {
        public void dispose() {
          focusableComponent.removeFocusListener(listener);
        }
      });
      return listener;
    }
  }

  public JComponent getFocusableComponent() {
    Editor editor = getEditor();
    return editor != null ? editor.getContentComponent() : MOCK_COMPONENT;
  }

  public void becomeMaster() {
    if (myIsMaster) return;
    myIsMaster = true;
    myContainer.setCurrentSide(this);
    beMaster();
  }

  private void beMaster() {
    myLineMarker.set();
  }

  public void scrollToFirstDiff(int logicalLine) {
    Editor editor = myEditorSource.getEditor();
    SyncScrollSupport.scrollEditor(editor, logicalLine);
  }

  public Editor getEditor() {
    return myEditorSource.getEditor();
  }

  public FragmentSide getSide() {
    return myEditorSource.getSide();
  }

  private class MyState {
    private final boolean isFocused;
    public MyState() {
      isFocused = IJSwingUtilities.hasFocus(getFocusableComponent());
    }

    public void restore() {
      if (isFocused) getFocusableComponent().requestFocus();
      if (myIsMaster) beMaster();
    }
  }
}
