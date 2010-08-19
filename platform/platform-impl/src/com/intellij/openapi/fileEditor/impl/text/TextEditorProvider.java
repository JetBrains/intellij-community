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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class TextEditorProvider implements FileEditorProvider, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.TextEditorProvider");
  private static final Key<TextEditor> TEXT_EDITOR_KEY = Key.create("textEditor");

  @NonNls private static final String TYPE_ID = "text-editor";
  @NonNls private static final String LINE_ATTR = "line";
  @NonNls private static final String COLUMN_ATTR = "column";
  @NonNls private static final String SELECTION_START_ATTR = "selection-start";
  @NonNls private static final String SELECTION_END_ATTR = "selection-end";
  @NonNls private static final String VERTICAL_SCROLL_PROPORTION_ATTR = "vertical-scroll-proportion";

  public static TextEditorProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(TextEditorProvider.class);
  }

  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.isDirectory() || !file.isValid()) {
      return false;
    }

    final FileType ft = file.getFileType();
    return !ft.isBinary() || BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) != null;
  }

  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file) {
    LOG.assertTrue(accept(project, file));
    return new TextEditorImpl(project, file, this);
  }

  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  public FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    TextEditorState state = new TextEditorState();

    try {
      state.LINE = Integer.parseInt(element.getAttributeValue(LINE_ATTR));
      state.COLUMN = Integer.parseInt(element.getAttributeValue(COLUMN_ATTR));
      state.SELECTION_START = Integer.parseInt(element.getAttributeValue(SELECTION_START_ATTR));
      state.SELECTION_END = Integer.parseInt(element.getAttributeValue(SELECTION_END_ATTR));
      state.VERTICAL_SCROLL_PROPORTION = Float.parseFloat(element.getAttributeValue(VERTICAL_SCROLL_PROPORTION_ATTR));
    }
    catch (NumberFormatException ignored) {
    }

    return state;
  }

  public void writeState(@NotNull FileEditorState _state, @NotNull Project project, @NotNull Element element) {
    TextEditorState state = (TextEditorState)_state;

    element.setAttribute(LINE_ATTR, Integer.toString(state.LINE));
    element.setAttribute(COLUMN_ATTR, Integer.toString(state.COLUMN));
    element.setAttribute(SELECTION_START_ATTR, Integer.toString(state.SELECTION_START));
    element.setAttribute(SELECTION_END_ATTR, Integer.toString(state.SELECTION_END));
    element.setAttribute(VERTICAL_SCROLL_PROPORTION_ATTR, Float.toString(state.VERTICAL_SCROLL_PROPORTION));
  }

  @NotNull
  public String getEditorTypeId() {
    return TYPE_ID;
  }

  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.NONE;
  }

  @NotNull public TextEditor getTextEditor(@NotNull Editor editor) {
    TextEditor textEditor = editor.getUserData(TEXT_EDITOR_KEY);
    if (textEditor == null) {
      textEditor = createWrapperForEditor(editor);
      putTextEditor(editor, textEditor);
    }

    return textEditor;
  }

  protected EditorWrapper createWrapperForEditor(final Editor editor) {
    return new EditorWrapper(editor);
  }

  @Nullable
  public static Document[] getDocuments(@NotNull FileEditor editor) {
    if (editor instanceof DocumentsEditor) {
      DocumentsEditor documentsEditor = (DocumentsEditor)editor;
      Document[] documents = documentsEditor.getDocuments();
      return documents.length > 0 ? documents : null;
    }

    if (editor instanceof TextEditor) {
      Document document = ((TextEditor)editor).getEditor().getDocument();
      return new Document[]{document};
    }

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = projects.length - 1; i >= 0; i--) {
      VirtualFile file = FileEditorManagerEx.getInstanceEx(projects[i]).getFile(editor);
      if (file != null) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          return new Document[]{document};
        }
      }
    }

    return null;
  }

  static void putTextEditor(Editor editor, TextEditor textEditor) {
    editor.putUserData(TEXT_EDITOR_KEY, textEditor);
  }

  protected TextEditorState getStateImpl(final Project project, final Editor editor, @NotNull FileEditorStateLevel level){
    TextEditorState state = new TextEditorState();
    state.LINE = editor.getCaretModel().getLogicalPosition().line;
    state.COLUMN = editor.getCaretModel().getLogicalPosition().column;
    state.SELECTION_START = editor.getSelectionModel().getSelectionStart();
    state.SELECTION_END = editor.getSelectionModel().getSelectionEnd();

    // Saving scrolling proportion on UNDO may cause undesirable results of undo action fails to perform since
    // scrolling proportion restored slightly differs from what have been saved.
    state.VERTICAL_SCROLL_PROPORTION = level == FileEditorStateLevel.UNDO ? -1 : EditorUtil.calcVerticalScrollProportion(editor);
    return state;
  }

  protected void setStateImpl(final Project project, final Editor editor, final TextEditorState state){
    LogicalPosition pos = new LogicalPosition(state.LINE, state.COLUMN);
    editor.getCaretModel().moveToLogicalPosition(pos);
    editor.getSelectionModel().removeSelection();
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    if (state.VERTICAL_SCROLL_PROPORTION != -1) {
      EditorUtil.setVerticalScrollProportion(editor, state.VERTICAL_SCROLL_PROPORTION);
    }

    final Document document = editor.getDocument();

    if (state.SELECTION_START == state.SELECTION_END) {
      editor.getSelectionModel().removeSelection();
    }
    else {
      int startOffset = Math.min(state.SELECTION_START, document.getTextLength());
      int endOffset = Math.min(state.SELECTION_END, document.getTextLength());
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
    ((EditorEx) editor).stopOptimizedScrolling();
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  protected class EditorWrapper extends UserDataHolderBase implements TextEditor {
    private final Editor myEditor;

    public EditorWrapper(Editor editor) {
      myEditor = editor;
    }

    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    @NotNull
    public JComponent getComponent() {
      return myEditor.getComponent();
    }

    public JComponent getPreferredFocusedComponent() {
      return myEditor.getContentComponent();
    }

    @NotNull
    public String getName() {
      return "Text";
    }

    public StructureViewBuilder getStructureViewBuilder() {
      VirtualFile file = FileDocumentManager.getInstance().getFile(myEditor.getDocument());
      if (file == null) return null;
      
      final Project project = myEditor.getProject();
      LOG.assertTrue(project != null);
      return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.getFileType(), file, project);
    }

    @NotNull
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
      return getStateImpl(null, myEditor, level);
    }

    public void setState(@NotNull FileEditorState state) {
      setStateImpl(null, myEditor, (TextEditorState)state);
    }

    public boolean isModified() {
      return false;
    }

    public boolean isValid() {
      return true;
    }

    public void dispose() { }

    public void selectNotify() { }

    public void deselectNotify() { }

    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return null;
    }

    public FileEditorLocation getCurrentLocation() {
      return null;
    }

    public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
      return false;
    }

    public void navigateTo(@NotNull final Navigatable navigatable) {
    }
  }
}
