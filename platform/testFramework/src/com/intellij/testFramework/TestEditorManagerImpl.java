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
package com.intellij.testFramework;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@NonNls public class TestEditorManagerImpl extends FileEditorManagerEx implements ApplicationComponent, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.test.TestEditorManagerImpl");

  private final Project myProject;

  private final Map<VirtualFile, Editor> myVirtualFile2Editor = new HashMap<VirtualFile,Editor>();
  private VirtualFile myActiveFile = null;
  private static final LightVirtualFile LIGHT_VIRTUAL_FILE = new LightVirtualFile("Dummy.java");

  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file, boolean focusEditor) {
    Editor editor = openTextEditor(new OpenFileDescriptor(myProject, file), focusEditor);
    final FileEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    return Pair.create (new FileEditor[] {fileEditor}, new FileEditorProvider[] {getProvider (fileEditor)});
  }

  public boolean isInsideChange() {
    return false;
  }

  public void createSplitter(int orientation, EditorWindow window) {

  }

  public void changeSplitterOrientation() {

  }

  public void flipTabs() {

  }

  public boolean tabsMode() {
    return false;
  }

  public boolean isInSplitter() {
    return false;
  }

  public boolean hasOpenedFile() {
    return false;
  }

  public VirtualFile getCurrentFile() {
    return null;
  }

  public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file) {
    return null;
  }

  public boolean isChanged(@NotNull EditorComposite editor) {
    return false;
  }

  public EditorWindow getNextWindow(@NotNull EditorWindow window) {
    return null;
  }

  public EditorWindow getPrevWindow(@NotNull EditorWindow window) {
    return null;
  }

  public void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  public void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  public void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  public void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
  }

  public void closeAllFiles() {
    final EditorFactory editorFactory = EditorFactory.getInstance();
    for (Editor editor : myVirtualFile2Editor.values()) {
      if (editor != null && !editor.isDisposed()){
        editorFactory.releaseEditor(editor);
      }
    }
    myVirtualFile2Editor.clear();
  }

  public Editor openTextEditorEnsureNoFocus(@NotNull OpenFileDescriptor descriptor) {
    return openTextEditor(descriptor, false);
  }

  private FileEditorProvider getProvider(FileEditor editor) {
    return new FileEditorProvider() {
      public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return false;
      }

      @NotNull
      public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return null;
      }

      public void disposeEditor(@NotNull FileEditor editor) {
        //Disposer.dispose(editor);
      }

      @NotNull
      public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
        return null;
      }

      public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {

      }

      @NotNull
      public String getEditorTypeId() {
        return "";
      }

      @NotNull
      public FileEditorPolicy getPolicy() {
        return null;
      }
    };
  }

  public EditorWindow getCurrentWindow() {
    return null;
  }

  public void setCurrentWindow(EditorWindow window) {
  }

  public VirtualFile getFile(@NotNull FileEditor editor) {
    return LIGHT_VIRTUAL_FILE;
  }

  public void updateFilePresentation(VirtualFile file) {
  }

  public void unsplitWindow() {

  }

  public void unsplitAllWindow() {

  }

  @NotNull
  public EditorWindow[] getWindows() {
    return new EditorWindow[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  public FileEditor getSelectedEditor(@NotNull VirtualFile file) {
    final Editor editor = getEditor(file);
    return editor == null ? null : TextEditorProvider.getInstance().getTextEditor(editor);
  }

  public boolean isFileOpen(@NotNull VirtualFile file) {
    return getEditor(file) != null;
  }

  @NotNull
  public FileEditor[] getEditors(@NotNull VirtualFile file) {
    FileEditor e = getSelectedEditor(file);
    if (e == null) return new FileEditor[0];
    return new FileEditor[] {e};
  }

  @NotNull
  @Override
  public FileEditor[] getAllEditors(@NotNull VirtualFile file) {
    return getEditors(file);
  }

  public TestEditorManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public VirtualFile[] getSiblings(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  public void disposeComponent() {
    closeAllFiles();
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void closeFile(@NotNull VirtualFile file) {
    Editor editor = myVirtualFile2Editor.get(file);
    if (editor != null){
      EditorFactory.getInstance().releaseEditor(editor);
      myVirtualFile2Editor.remove(file);
    }
    if (file == myActiveFile) myActiveFile = null;
  }

  public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    closeFile(file);
  }

  @NotNull
  public VirtualFile[] getSelectedFiles() {
    return myActiveFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{myActiveFile};
  }

  @NotNull
  public FileEditor[] getSelectedEditors() {
    return new FileEditor[0];
  }

  public Editor getSelectedTextEditor() {
    return myActiveFile != null ? getEditor(myActiveFile) : null;
  }

  public JComponent getComponent() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public VirtualFile[] getOpenFiles() {
    return myVirtualFile2Editor.keySet().toArray(new VirtualFile[myVirtualFile2Editor.size()]);
  }

  public Editor getEditor(VirtualFile file) {
    return myVirtualFile2Editor.get(file);
  }

  @NotNull
  public FileEditor[] getAllEditors(){
    throw new UnsupportedOperationException();
  }

  public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
  }


  public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
  }

  public void registerFileAsOpened(VirtualFile file, Editor editor) {
    myVirtualFile2Editor.put(file, editor);
    myActiveFile = file;
  }

  public Editor openTextEditor(OpenFileDescriptor descriptor, boolean focusEditor) {
    final VirtualFile file = descriptor.getFile();
    Editor editor = myVirtualFile2Editor.get(file);

    if (editor == null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      LOG.assertTrue(psiFile != null);
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
      editor = EditorFactory.getInstance().createEditor(document, myProject);
      ((EditorEx) editor).setHighlighter(HighlighterFactory.createHighlighter(myProject, file));
      ((EditorEx) editor).setFile(file);

      myVirtualFile2Editor.put(file, editor);
    }

    if (descriptor.getOffset() >= 0){
      editor.getCaretModel().moveToOffset(descriptor.getOffset());
    }
    else if (descriptor.getLine() >= 0 && descriptor.getColumn() >= 0){
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(descriptor.getLine(), descriptor.getColumn()));
    }
    editor.getSelectionModel().removeSelection();
    myActiveFile = file;

    return editor;
  }

  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
  }

  public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, Disposable parentDisposable) {
  }

  public void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
  }

  @NotNull
  public List<FileEditor> openEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    return Collections.emptyList();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void registerExtraEditorDataProvider(@NotNull EditorDataProvider provider, Disposable parentDisposable) {
  }

  public JComponent getPreferredFocusedComponent() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public int getWindowSplitCount() {
    return 0;
  }

  @NotNull
  public String getComponentName() {
    return "TestEditorManager";
  }
}
