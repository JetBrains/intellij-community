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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class TextEditorProvider implements FileEditorProvider, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.TextEditorProvider");

  private static final Key<TextEditor> TEXT_EDITOR_KEY = Key.create("textEditor");

  @NonNls private static final String TYPE_ID                         = "text-editor";
  @NonNls private static final String LINE_ATTR                       = "line";
  @NonNls private static final String COLUMN_ATTR                     = "column";
  @NonNls private static final String LEAN_FORWARD_ATTR               = "lean-forward";
  @NonNls private static final String SELECTION_START_LINE_ATTR       = "selection-start-line";
  @NonNls private static final String SELECTION_START_COLUMN_ATTR     = "selection-start-column";
  @NonNls private static final String SELECTION_END_LINE_ATTR         = "selection-end-line";
  @NonNls private static final String SELECTION_END_COLUMN_ATTR       = "selection-end-column";
  @NonNls private static final String RELATIVE_CARET_POSITION_ATTR    = "relative-caret-position";
  @NonNls private static final String CARET_ELEMENT                   = "caret";

  public static TextEditorProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(TextEditorProvider.class);
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return isTextFile(file) && !SingleRootFileViewProvider.isTooLargeForContentLoading(file);
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file) {
    LOG.assertTrue(accept(project, file));
    return new TextEditorImpl(project, file, this);
  }

  @Override
  @NotNull
  public FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    TextEditorState state = new TextEditorState();

    try {
      List<Element> caretElements = element.getChildren(CARET_ELEMENT);
      if (caretElements.isEmpty()) {
        state.CARETS = new TextEditorState.CaretState[] {readCaretInfo(element)};
      }
      else {
        state.CARETS = new TextEditorState.CaretState[caretElements.size()];
        for (int i = 0; i < caretElements.size(); i++) {
          state.CARETS[i] = readCaretInfo(caretElements.get(i));
        }
      }

      String verticalScrollProportion = element.getAttributeValue(RELATIVE_CARET_POSITION_ATTR);
      state.RELATIVE_CARET_POSITION = verticalScrollProportion == null ? 0 : Integer.parseInt(verticalScrollProportion);
    }
    catch (NumberFormatException ignored) {
    }

    return state;
  }

  private static TextEditorState.CaretState readCaretInfo(Element element) {
    TextEditorState.CaretState caretState = new TextEditorState.CaretState();
    caretState.LINE = parseWithDefault(element, LINE_ATTR);
    caretState.COLUMN = parseWithDefault(element, COLUMN_ATTR);
    caretState.LEAN_FORWARD = parseBooleanWithDefault(element, LEAN_FORWARD_ATTR);
    caretState.SELECTION_START_LINE = parseWithDefault(element, SELECTION_START_LINE_ATTR);
    caretState.SELECTION_START_COLUMN = parseWithDefault(element, SELECTION_START_COLUMN_ATTR);
    caretState.SELECTION_END_LINE = parseWithDefault(element, SELECTION_END_LINE_ATTR);
    caretState.SELECTION_END_COLUMN = parseWithDefault(element, SELECTION_END_COLUMN_ATTR);
    return caretState;
  }

  private static int parseWithDefault(Element element, String attributeName) {
    String value = element.getAttributeValue(attributeName);
    return value == null ? 0 : Integer.parseInt(value);
  }

  private static boolean parseBooleanWithDefault(Element element, String attributeName) {
    String value = element.getAttributeValue(attributeName);
    return value != null && Boolean.parseBoolean(value);
  }

  @Override
  public void writeState(@NotNull FileEditorState _state, @NotNull Project project, @NotNull Element element) {
    TextEditorState state = (TextEditorState)_state;

    element.setAttribute(RELATIVE_CARET_POSITION_ATTR, Integer.toString(state.RELATIVE_CARET_POSITION));
    if (state.CARETS != null) {
      for (TextEditorState.CaretState caretState : state.CARETS) {
        Element e = new Element(CARET_ELEMENT);
        e.setAttribute(LINE_ATTR, Integer.toString(caretState.LINE));
        e.setAttribute(COLUMN_ATTR, Integer.toString(caretState.COLUMN));
        e.setAttribute(LEAN_FORWARD_ATTR, Boolean.toString(caretState.LEAN_FORWARD));
        e.setAttribute(SELECTION_START_LINE_ATTR, Integer.toString(caretState.SELECTION_START_LINE));
        e.setAttribute(SELECTION_START_COLUMN_ATTR, Integer.toString(caretState.SELECTION_START_COLUMN));
        e.setAttribute(SELECTION_END_LINE_ATTR, Integer.toString(caretState.SELECTION_END_LINE));
        e.setAttribute(SELECTION_END_COLUMN_ATTR, Integer.toString(caretState.SELECTION_END_COLUMN));
        element.addContent(e);
      }
    }
  }

  @Override
  @NotNull
  public String getEditorTypeId() {
    return TYPE_ID;
  }

  @Override
  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.NONE;
  }

  @NotNull
  public TextEditor getTextEditor(@NotNull Editor editor) {
    TextEditor textEditor = editor.getUserData(TEXT_EDITOR_KEY);
    if (textEditor == null) {
      textEditor = createWrapperForEditor(editor);
      putTextEditor(editor, textEditor);
    }

    return textEditor;
  }

  @NotNull
  protected EditorWrapper createWrapperForEditor(@NotNull Editor editor) {
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

  @NotNull
  protected TextEditorState getStateImpl(final Project project, @NotNull Editor editor, @NotNull FileEditorStateLevel level){
    TextEditorState state = new TextEditorState();
    CaretModel caretModel = editor.getCaretModel();
    List<CaretState> caretsAndSelections = caretModel.getCaretsAndSelections();
    state.CARETS = new TextEditorState.CaretState[caretsAndSelections.size()];
    for (int i = 0; i < caretsAndSelections.size(); i++) {
      CaretState caretState = caretsAndSelections.get(i);
      LogicalPosition caretPosition = caretState.getCaretPosition();
      LogicalPosition selectionStartPosition = caretState.getSelectionStart();
      LogicalPosition selectionEndPosition = caretState.getSelectionEnd();
      
      TextEditorState.CaretState s = new TextEditorState.CaretState();
      s.LINE = getLine(caretPosition);
      s.COLUMN = getColumn(caretPosition);
      s.LEAN_FORWARD = caretPosition != null && caretPosition.leansForward;
      s.VISUAL_COLUMN_ADJUSTMENT = caretState.getVisualColumnAdjustment();
      s.SELECTION_START_LINE = getLine(selectionStartPosition);
      s.SELECTION_START_COLUMN = getColumn(selectionStartPosition);
      s.SELECTION_END_LINE = getLine(selectionEndPosition);
      s.SELECTION_END_COLUMN = getColumn(selectionEndPosition);
      state.CARETS[i] = s;
    }

    // Saving scrolling proportion on UNDO may cause undesirable results of undo action fails to perform since
    // scrolling proportion restored slightly differs from what have been saved.
    state.RELATIVE_CARET_POSITION = level == FileEditorStateLevel.UNDO ? Integer.MAX_VALUE : EditorUtil.calcRelativeCaretPosition(editor);

    return state;
  }

  public static boolean isTextFile(@NotNull VirtualFile file) {
    if (file.isDirectory() || !file.isValid()) {
      return false;
    }

    final FileType ft = file.getFileType();
    return !ft.isBinary() || BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) != null;
  }

  private static int getLine(@Nullable LogicalPosition pos) {
    return pos == null ? 0 : pos.line;
  }

  private static int getColumn(@Nullable LogicalPosition pos) {
    return pos == null ? 0 : pos.column;
  }

  protected void setStateImpl(final Project project, final Editor editor, final TextEditorState state){
    TextEditorState.CaretState[] carets = state.CARETS;
    if (carets != null && carets.length > 0) {
      if (!editor.getCaretModel().supportsMultipleCarets()) carets = Arrays.copyOf(carets, 1);
      CaretModel caretModel = editor.getCaretModel();
      List<CaretState> states = new ArrayList<>(carets.length);
      for (TextEditorState.CaretState caretState : carets) {
        states.add(new CaretState(new LogicalPosition(caretState.LINE, caretState.COLUMN, caretState.LEAN_FORWARD),
                                  caretState.VISUAL_COLUMN_ADJUSTMENT,
                                  new LogicalPosition(caretState.SELECTION_START_LINE, caretState.SELECTION_START_COLUMN),
                                  new LogicalPosition(caretState.SELECTION_END_LINE, caretState.SELECTION_END_COLUMN)));
      }
      caretModel.setCaretsAndSelections(states, false);
    }

    final int relativeCaretPosition = state.RELATIVE_CARET_POSITION;
    Runnable scrollingRunnable = () -> {
      if (!editor.isDisposed()) {
        editor.getScrollingModel().disableAnimation();
        if (relativeCaretPosition != Integer.MAX_VALUE) {
          EditorUtil.setRelativeCaretPosition(editor, relativeCaretPosition);
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getScrollingModel().enableAnimation();
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) scrollingRunnable.run();
    else UiNotifyConnector.doWhenFirstShown(editor.getContentComponent(), scrollingRunnable);
  }

  protected class EditorWrapper extends UserDataHolderBase implements TextEditor {
    private final Editor myEditor;

    EditorWrapper(@NotNull Editor editor) {
      myEditor = editor;
    }

    @Override
    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    @Override
    @NotNull
    public JComponent getComponent() {
      return myEditor.getComponent();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myEditor.getContentComponent();
    }

    @Override
    @NotNull
    public String getName() {
      return "Text";
    }

    @Override
    public StructureViewBuilder getStructureViewBuilder() {
      VirtualFile file = getFile();
      if (file == null) return null;

      final Project project = myEditor.getProject();
      LOG.assertTrue(project != null);
      return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.getFileType(), file, project);
    }

    @Override
    @NotNull
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
      return getStateImpl(null, myEditor, level);
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
      setStateImpl(null, myEditor, (TextEditorState)state);
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public void dispose() { }

    @Override
    public void selectNotify() { }

    @Override
    public void deselectNotify() { }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }

    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return null;
    }

    @Override
    public FileEditorLocation getCurrentLocation() {
      return null;
    }

    @Override
    public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
      return false;
    }

    @Override
    public void navigateTo(@NotNull final Navigatable navigatable) {
    }

    @Nullable
    @Override
    public VirtualFile getFile() {
      return FileDocumentManager.getInstance().getFile(myEditor.getDocument());
    }
  }
}
