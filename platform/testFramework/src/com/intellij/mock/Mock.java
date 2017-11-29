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
package com.intellij.mock;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.IdeFocusManagerHeadless;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
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
    public Document[] DOCUMENTS;

    @Override
    public Document[] getDocuments() {
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
    public void selectNotify() {
    }

    @Override
    public void deselectNotify() {
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
      throw new RuntimeException("not implemented");
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
      throw new RuntimeException("not implemented");
    }

    @NotNull
    @Override
    public AsyncResult<EditorWindow> getActiveWindow() {
      throw new RuntimeException("not implemented");
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
    public void updateFilePresentation(@NotNull VirtualFile file) {
    }

    @Override
    public void unsplitWindow() {

    }

    @Override
    public void unsplitAllWindow() {

    }

    @Override
    @NotNull
    public EditorWindow[] getWindows() {
      return new EditorWindow[0];
    }

    @Override
    @NotNull
    public VirtualFile[] getSiblings(@NotNull VirtualFile file) {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public void createSplitter(int orientation, @Nullable EditorWindow window) {

    }

    @Override
    public void changeSplitterOrientation() {

    }

    @Override
    public void flipTabs() {

    }

    @Override
    public boolean tabsMode() {
      return false;
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
    public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file) {
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
      return Pair.create(new FileEditor[0], new FileEditorProvider[0]);
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
    @NotNull
    public VirtualFile[] getOpenFiles() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    @NotNull
    public VirtualFile[] getSelectedFiles() {
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    @NotNull
    public FileEditor[] getSelectedEditors() {
      return new FileEditor[0];
    }

    @Override
    public FileEditor getSelectedEditor(@NotNull VirtualFile file) {
      return null;
    }

    @Override
    @NotNull
    public FileEditor[] getEditors(@NotNull VirtualFile file) {
      return new FileEditor[0];
    }

    @NotNull
    @Override
    public FileEditor[] getAllEditors(@NotNull VirtualFile file) {
      return new FileEditor[0];
    }

    @Override
    @NotNull
    public FileEditor[] getAllEditors() {
      return new FileEditor[0];
    }

    @Override
    public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
    }

    @Override
    public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComponent) {
    }

    @Override
    public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
    }

    @Override
    public void addFileEditorManagerListener(@NotNull FileEditorManagerListener listener, @NotNull Disposable parentDisposable) {
    }

    @Override
    public void removeFileEditorManagerListener(@NotNull FileEditorManagerListener listener) {
    }

    @Override
    @NotNull
    public List<FileEditor> openEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
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
    public void rename(Object requestor, @NotNull String newName) throws IOException {
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
    public void delete(Object requestor) throws IOException {
    }

    @Override
    public void move(Object requestor, @NotNull VirtualFile newParent) throws IOException {
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return null;
    }

    @Override
    @NotNull
    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public byte[] contentsToByteArray() throws IOException {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
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

  public static class MyToolWindowManager extends ToolWindowManager {

    @Override
    public boolean canShowNotification(@NotNull String toolWindowId) {
      return false;
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull String id,
                                         @NotNull JComponent component,
                                         @NotNull ToolWindowAnchor anchor,
                                         @NotNull Disposable parentDisposable,
                                         boolean canWorkInDumbMode, boolean canCloseContents) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull String id,
                                         @NotNull JComponent component,
                                         @NotNull ToolWindowAnchor anchor,
                                         @NotNull Disposable parentDisposable,
                                         boolean canWorkInDumbMode) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor, @NotNull Disposable parentDisposable) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor,
                                         @NotNull final Disposable parentDisposable, final boolean dumbAware) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull String id,
                                         boolean canCloseContent,
                                         @NotNull ToolWindowAnchor anchor,
                                         @NotNull Disposable parentDisposable,
                                         boolean canWorkInDumbMode,
                                         boolean secondary) {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor, final boolean secondary) {
      throw new RuntimeException();
    }

    @Override
    public void unregisterToolWindow(@NotNull String id) {
    }

    @Override
    public void activateEditorComponent() {
    }

    @Override
    public boolean isEditorComponentActive() {
      return false;
    }

    @NotNull
    @Override
    public String[] getToolWindowIds() {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public String getActiveToolWindowId() {
      return null;
    }

    @Override
    public ToolWindow getToolWindow(String id) {
      return null;
    }

    @Override
    public void invokeLater(@NotNull Runnable runnable) {
    }

    @NotNull
    @Override
    public IdeFocusManager getFocusManager() {
      return IdeFocusManagerHeadless.INSTANCE;
    }

    @Override
    public void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String text, @Nullable final Icon icon,
                                @Nullable final HyperlinkListener listener) {
    }

    @Override
    public Balloon getToolWindowBalloon(String id) {
      return null;
    }

    @Override
    public boolean isMaximized(@NotNull ToolWindow wnd) {
      return false;
    }

    @Override
    public void setMaximized(@NotNull ToolWindow wnd, boolean maximized) {
    }

    @Override
    public void notifyByBalloon(@NotNull final String toolWindowId, @NotNull final MessageType type, @NotNull final String htmlBody) {
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
