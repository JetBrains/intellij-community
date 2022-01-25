// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

//[kirillk] - this class looks to be an overkill but IdeDocumentHistory is highly coupled
// with all of that stuff below, so it's not possible to test it's back/forward capabilities
// w/o making mocks for all of them. perhaps later we will decouple those things
public class Mock {
  public static class MyFileEditor extends UserDataHolderBase implements DocumentsEditor {
    private final Document @NotNull [] DOCUMENTS;

    public MyFileEditor(Document @NotNull ... DOCUMENTS) {
      this.DOCUMENTS = DOCUMENTS;
    }
    public MyFileEditor() {
      this(Document.EMPTY_ARRAY);
    }

    @Override
    public Document @NotNull [] getDocuments() {
      return DOCUMENTS;
    }

    @Override
    @NotNull
    public JComponent getComponent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Override
    @NotNull
    public String getName() {
      return "";
    }

    @Override
    public void dispose() {
    }

    @Override
    public StructureViewBuilder getStructureViewBuilder() {
      return null;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
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
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return null;
    }

    @Override
    public FileEditorLocation getCurrentLocation() {
      return null;
    }
  }

  public static class MyFileEditorManager extends FileEditorManagerEx {
    @Override
    public JComponent getComponent() {
      return null;
    }

    @NotNull
    @Override
    public ActionCallback notifyPublisher(@NotNull Runnable runnable) {
      runnable.run();
      return ActionCallback.DONE;
    }

    @NotNull
    @Override
    public ActionCallback getReady(@NotNull Object requestor) {
      return ActionCallback.DONE;
    }

    @NotNull
    @Override
    public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                          boolean focusEditor,
                                                                          @NotNull EditorWindow window) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInsideChange() {
      return false;
    }

    @Override
    public boolean hasSplitOrUndockedWindows() {
      return false;
    }

    @Override
    public EditorsSplitters getSplittersFor(Component c) {
      return null;
    }

    @NotNull
    @Override
    public EditorsSplitters getSplitters() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Promise<EditorWindow> getActiveWindow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    }

    @Override
    public void removeTopComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    }

    @Override
    public void addBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    }

    @Override
    public void removeBottomComponent(@NotNull final FileEditor editor, @NotNull final JComponent component) {
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Override
    @NotNull
    public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    public FileEditorProvider getProvider(FileEditor editor) {
      return null;
    }

    @Override
    public EditorWindow getCurrentWindow() {
      return null;
    }

    @Override
    public void setCurrentWindow(EditorWindow window) {
    }

    @Override
    public VirtualFile getFile(@NotNull FileEditor editor) {
      return null;
    }

    @Override
    public void unsplitWindow() {

    }

    @Override
    public void unsplitAllWindow() {

    }

    @Override
    public EditorWindow @NotNull [] getWindows() {
      return new EditorWindow[0];
    }

    @Override
    public VirtualFile @NotNull [] getSiblings(@NotNull VirtualFile file) {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public void createSplitter(int orientation, @Nullable EditorWindow window) {

    }

    @Override
    public void changeSplitterOrientation() {

    }

    @Override
    public boolean isInSplitter() {
      return false;
    }

    @Override
    public boolean hasOpenedFile() {
      return false;
    }

    @Override
    public VirtualFile getCurrentFile() {
      return null;
    }

    @Override
    public FileEditorWithProvider getSelectedEditorWithProvider(@NotNull VirtualFile file) {
      return null;
    }

    @Override
    public boolean isChanged(@NotNull EditorComposite editor) {
      return false;
    }

    @Override
    public EditorWindow getNextWindow(@NotNull EditorWindow window) {
      return null;
    }

    @Override
    public EditorWindow getPrevWindow(@NotNull EditorWindow window) {
      return null;
    }

    @Override
    public void closeAllFiles() {
    }

    @Override
    @NotNull
    public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                          boolean focusEditor,
                                                                          boolean searchForSplitter) {
      return Pair.create(FileEditor.EMPTY_ARRAY, new FileEditorProvider[0]);
    }

    @Override
    public void closeFile(@NotNull VirtualFile file) {
    }

    @Override
    public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    }

    @Override
    public Editor openTextEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
      return null;
    }

    @Override
    public Editor getSelectedTextEditor() {
      return null;
    }

    @Override
    public boolean isFileOpen(@NotNull VirtualFile file) {
      return false;
    }

    @Override
    public VirtualFile @NotNull [] getOpenFiles() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public VirtualFile @NotNull [] getSelectedFiles() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public FileEditor @NotNull [] getSelectedEditors() {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    public FileEditor getSelectedEditor(@NotNull VirtualFile file) {
      return null;
    }

    @Override
    public FileEditor @NotNull [] getEditors(@NotNull VirtualFile file) {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    public FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file) {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    public FileEditor @NotNull [] getAllEditors() {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    @NotNull
    public List<FileEditor> openFileEditor(@NotNull FileEditorNavigatable descriptor, boolean focusEditor) {
      return Collections.emptyList();
    }

    @Override
    @NotNull
    public Project getProject() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void registerExtraEditorDataProvider(@NotNull EditorDataProvider provider, Disposable parentDisposable) {
    }

    @Override
    public int getWindowSplitCount() {
      return 0;
    }

    @Override
    public void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId) {
    }
  }

  public static class MyVirtualFile extends VirtualFile {

    public boolean myValid = true;

    @Override
    @NotNull
    public VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String getPath() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void rename(Object requestor, @NotNull String newName) {
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public boolean isValid() {
      return myValid;
    }

    @Override
    public VirtualFile getParent() {
      return null;
    }

    @Override
    public VirtualFile[] getChildren() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
      throw new IOException(name);
    }

    @NotNull
    @Override
    public VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
      throw new IOException(name);
    }

    @Override
    public void delete(Object requestor) {
    }

    @Override
    public void move(Object requestor, @NotNull VirtualFile newParent) {
    }

    @Override
    public @NotNull InputStream getInputStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte @NotNull [] contentsToByteArray() {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }

    @Override
    public long getModificationStamp() {
      return 0;
    }

    @Override
    public long getTimeStamp() {
      return 0;
    }

    @Override
    public long getLength() {
      return 0;
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    }
  }

  public static class MyFileEditorProvider implements FileEditorProvider {
    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
      return false;
    }

    @Override
    @NotNull
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {
    }

    @Override
    @NotNull
    public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public String getEditorTypeId() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public FileEditorPolicy getPolicy() {
      throw new UnsupportedOperationException();
    }
  }
}
