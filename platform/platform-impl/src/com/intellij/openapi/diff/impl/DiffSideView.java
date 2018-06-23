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
package com.intellij.openapi.diff.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.LabeledEditor;
import com.intellij.openapi.diff.impl.util.SyncScrollSupport;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.ScrollUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

public class DiffSideView {
  private final JComponent MOCK_COMPONENT = new JPanel();
  {
    MOCK_COMPONENT.setFocusable(true);
  }

  private static final DiffHighlighterFactory DUMMY_HIGHLIGHTER_FACTORY = new DiffHighlighterFactoryImpl(null, null, null);
  private final LabeledEditor myPanel;

  private final DiffSidesContainer myContainer;
  private final CurrentLineMarker myLineMarker = new CurrentLineMarker();

  private DiffHighlighterFactory myHighlighterFactory = DUMMY_HIGHLIGHTER_FACTORY;
  private EditorSource myEditorSource = EditorSource.NULL;
  private boolean myIsMaster = false;
  private JComponent myTitle = new JLabel();

  public DiffSideView(DiffSidesContainer container, @Nullable Border editorBorder) {
    myContainer = container;
    myPanel = new LabeledEditor(editorBorder);
    insertComponent(MOCK_COMPONENT);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setEditorSource(final Project project, final EditorSource source) {
    MyState state = new MyState();
    myEditorSource = source;
    myLineMarker.attach(myEditorSource);
    Editor editor = myEditorSource.getEditor();
    final FileEditor fileEditor = myEditorSource.getFileEditor();
    if (editor == null) {
      insertComponent(fileEditor == null ? MOCK_COMPONENT : fileEditor.getComponent());
      DataManager.registerDataProvider(myPanel, new DataProvider() {
        @Override
        public Object getData(@NonNls String dataId) {
          if (CommonDataKeys.PROJECT.is(dataId)) {return project;}
          if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {return fileEditor;}
          return null;
        }
      });
      if (fileEditor != null) {
        ScrollUtil.scrollVertically(fileEditor.getComponent(), 0);
        ScrollUtil.scrollHorizontally(fileEditor.getComponent(), 0);
        UIUtil.removeScrollBorder(fileEditor.getComponent());
      }
    } else {
      DataManager.removeDataProvider(myPanel);
      editor.getScrollingModel().scrollHorizontally(0);
      insertComponent(editor.getComponent());
      applyHighlighter();
      setMouseListeners(source);
      MyEditorFocusListener.install(this);
      UIUtil.removeScrollBorder(editor.getComponent());

      state.restore();
    }
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
    editor.getSettings().setCaretRowShown(false);
  }

  public void setTitle(@NotNull JComponent title) {
    myTitle = title;
    myPanel.updateTitle(myTitle);
  }

  private void setMouseListeners(EditorSource source) {
    DiffContent content = source.getContent();
    MouseLineNumberListener.install(content, source, myContainer);
  }

  public void beSlave() {
    myIsMaster = false;
    myLineMarker.hide();
  }

  @Nullable
  public Navigatable getCurrentOpenFileDescriptor() {
    final EditorEx editor = myEditorSource.getEditor();
    final DiffContent content = myEditorSource.getContent();
    if (content == null || editor == null) {
      return null;
    }
    return content.getOpenFileDescriptor(editor.getCaretModel().getOffset());
  }

  private static class MouseLineNumberListener {
    private static final Cursor HAND__CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    private final Editor myEditor;
    private final DiffSidesContainer myContainer;
    private final DiffContent myContent;
    private final Project myProject;

    private final EditorMouseAdapter myMouseListener = new EditorMouseAdapter() {
      public void mouseReleased(EditorMouseEvent e) {
        if (!isEventHandled(e.getMouseEvent()) || !isInMyArea(e)) {
          return;
        }
        Navigatable descriptor = getOpenFileDescriptor(e);
        if (descriptor == null) {
          return;
        }
        myContainer.showSource(descriptor);
      }
    };

    private static boolean isEventHandled(MouseEvent e) {
      Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
      Shortcut[] shortcuts = activeKeymap.getShortcuts(IdeActions.ACTION_GOTO_DECLARATION);
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof MouseShortcut) {
          return ((MouseShortcut)shortcut).getButton() == e.getButton() && ((MouseShortcut)shortcut).getModifiers() == e.getModifiersEx();
        }
      }
      return false;
    }

    private Navigatable getOpenFileDescriptor(EditorMouseEvent e) {
      int offset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
      return myContent.getOpenFileDescriptor(offset);
    }

    private static boolean isInMyArea(EditorMouseEvent e) {
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
      if (project == null || editor == null) {
        return;
      }
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
    if (editor != null) return editor.getContentComponent();
    FileEditor fileEditor = myEditorSource.getFileEditor();
    if (fileEditor != null) return fileEditor.getComponent();
    return MOCK_COMPONENT;
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

  @Nullable
  public Editor getEditor() {
    return myEditorSource.getEditor();
  }

  @Nullable
  public FragmentSide getSide() {
    return myEditorSource.getSide();
  }

  private class MyState {
    private final boolean isFocused;
    public MyState() {
      isFocused = IJSwingUtilities.hasFocus(getFocusableComponent());
    }

    public void restore() {
      if (isFocused) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
          IdeFocusManager.getGlobalInstance().requestFocus(getFocusableComponent(), true);
        });
      }
      if (myIsMaster) beMaster();
    }
  }
}
