// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtilRt;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

//[kirillk] - this class looks to be an overkill but IdeDocumentHistory is highly coupled
// with all of that stuff below, so it's not possible to test its back/forward capabilities
// w/o making mocks for all of them. perhaps later we will decouple those things
public final class Mock {
  public static class MyFileEditor extends UserDataHolderBase implements DocumentsEditor {
    private final Document @NotNull [] DOCUMENTS;

    public MyFileEditor(@NotNull Document @NotNull ... DOCUMENTS) {
      this.DOCUMENTS = DOCUMENTS;
    }
    public MyFileEditor() {
      this(Document.EMPTY_ARRAY);
    }

    @Override
    public @NotNull Document @NotNull [] getDocuments() {
      return DOCUMENTS;
    }

    @Override
    public @NotNull JComponent getComponent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Override
    public @NotNull String getName() {
      return "";
    }

    @Override
    public void dispose() {
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
  }

  public static class MyFileEditorManager extends FileEditorManagerEx {
    @Override
    public JComponent getComponent() {
      return null;
    }

    @Override
    public void notifyPublisher(@NotNull Runnable runnable) {
      runnable.run();
    }

    @Override
    public boolean hasSplitOrUndockedWindows() {
      return false;
    }

    @Override
    public EditorsSplitters getSplittersFor(@NotNull Component component) {
      return null;
    }

    @Override
    public @NotNull EditorsSplitters getSplitters() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull CompletableFuture<EditorWindow> getActiveWindow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addTopComponent(final @NotNull FileEditor editor, final @NotNull JComponent component) {
    }

    @Override
    public void removeTopComponent(final @NotNull FileEditor editor, final @NotNull JComponent component) {
    }

    @Override
    public void addBottomComponent(final @NotNull FileEditor editor, final @NotNull JComponent component) {
    }

    @Override
    public void removeBottomComponent(final @NotNull FileEditor editor, final @NotNull JComponent component) {
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @Override
    public @NotNull Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable EditorComposite getComposite(@NotNull VirtualFile file) {
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
    public void unsplitAllWindow() {
    }

    @Override
    public EditorWindow @NotNull [] getWindows() {
      return new EditorWindow[0];
    }

    @Override
    public @NotNull List<VirtualFile> getSiblings(@NotNull VirtualFile file) {
      return Collections.emptyList();
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
    public @NotNull StateFlow<FileEditor> getSelectedEditorFlow() {
      return StateFlowKt.MutableStateFlow(null);
    }

    @Override
    public void closeFile(@NotNull VirtualFile file) {
    }

    @Override
    public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    }

    @Override
    public boolean closeFileWithChecks(@NotNull VirtualFile file, @NotNull EditorWindow window) {
      return true;
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
    public boolean canOpenFile(@NotNull VirtualFile file) {
      return false;
    }

    @Override
    public VirtualFile @NotNull [] getOpenFiles() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public @NotNull List<VirtualFile> getOpenFilesWithRemotes() {
      return Collections.emptyList();
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
    public FileEditor @NotNull [] getEditors(@NotNull VirtualFile file) {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    public FileEditor @NotNull [] getAllEditors(@NotNull VirtualFile file) {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull @Unmodifiable List<@NotNull FileEditor> getAllEditorList(@NotNull VirtualFile file) {
      return List.of();
    }

    @Override
    public FileEditor @NotNull [] getAllEditors() {
      return FileEditor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull List<FileEditor> openFileEditor(@NotNull FileEditorNavigatable descriptor, boolean focusEditor) {
      return Collections.emptyList();
    }

    @Override
    public @NotNull Project getProject() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getWindowSplitCount() {
      return 0;
    }

    @Override
    public void setSelectedEditor(@NotNull VirtualFile file, @NotNull String fileEditorProviderId) {
    }

    @Override
    public @NotNull FileEditorComposite openFile(@NotNull VirtualFile file, @Nullable EditorWindow window, @NotNull FileEditorOpenOptions options) {
      return FileEditorComposite.Companion.fromPair(new kotlin.Pair<>(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY));
    }

    @Override
    public @Nullable Object openFile(@NotNull VirtualFile file,
                                     @NotNull FileEditorOpenOptions options,
                                     @NotNull Continuation<? super FileEditorComposite> $completion) {
      return FileEditorComposite.Companion.fromPair(new kotlin.Pair<>(FileEditor.EMPTY_ARRAY, FileEditorProvider.EMPTY_ARRAY));
    }

    @Override
    public @Nullable Object openFileEditorAsync(@NotNull FileEditorNavigatable descriptor,
                                                boolean focusEditor,
                                                @NotNull Continuation<? super @NotNull List<? extends @NotNull FileEditor>> $completion) {
      return Collections.emptyList();
    }
  }

  public static class MyVirtualFile extends VirtualFile {

    public boolean myValid = true;

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull String getPath() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull String getName() {
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

    @Override
    public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
      throw new IOException(name);
    }

    @Override
    public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
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
    public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
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
    public boolean acceptRequiresReadAction() {
      return false;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void disposeEditor(@NotNull FileEditor editor) {
    }

    @Override
    public @NotNull FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull String getEditorTypeId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
      throw new UnsupportedOperationException();
    }
  }
}
