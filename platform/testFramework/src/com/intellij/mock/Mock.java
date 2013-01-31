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
import com.intellij.openapi.util.*;
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
    @NotNull
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
      return new FileEditorState() {
            @Override
            public boolean canBeMergedWith(FileEditorState fileEditorState, FileEditorStateLevel fileEditorStateLevel) {
                return false;
            }
        };
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
      return false;
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

    @Override
    public ActionCallback notifyPublisher(Runnable runnable) {
      runnable.run();
      return new ActionCallback.Done();
    }

    @Override
    public ActionCallback getReady(@NotNull Object requestor) {
      return new ActionCallback.Done();
    }

    @NotNull
    @Override
    public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                          boolean focusEditor,
                                                                          @NotNull EditorWindow window) {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
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

    @Override
    public EditorsSplitters getSplitters() {
      return null;
    }

    @Override
    public AsyncResult<EditorWindow> getActiveWindow() {
      return null;
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
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setCurrentWindow(EditorWindow window) {
    }

    @Override
    public VirtualFile getFile(@NotNull FileEditor editor) {
      return null;
    }

    @Override
    public void updateFilePresentation(VirtualFile file) {
    }

    @Override
    public void unsplitWindow() {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void unsplitAllWindow() {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    @NotNull
    public EditorWindow[] getWindows() {
      return new EditorWindow[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    @NotNull
    public VirtualFile[] getSiblings(VirtualFile file) {
      return new VirtualFile[0];
    }

    @Override
    public void createSplitter(int orientation, @Nullable EditorWindow window) {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void changeSplitterOrientation() {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void flipTabs() {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean tabsMode() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isInSplitter() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean hasOpenedFile() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public VirtualFile getCurrentFile() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Pair<FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file) {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isChanged(@NotNull EditorComposite editor) {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
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

    public Editor openTextEditorEnsureNoFocus(@NotNull OpenFileDescriptor descriptor) {
      return null;
    }

    @Override
    @NotNull
    public Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                         boolean focusEditor,
                                                                         boolean searchForSplitter) {
      return Pair.create (new FileEditor[0], new FileEditorProvider [0]);
    }

    @Override
    public void closeFile(@NotNull VirtualFile file) {
    }

    @Override
    public void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window) {
    }

    @Override
    public Editor openTextEditor(OpenFileDescriptor descriptor, boolean focusEditor) {
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
      return new VirtualFile[0];
    }

    @Override
    @NotNull
    public VirtualFile[] getSelectedFiles() {
      return new VirtualFile[0];
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
    public void removeEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
    }

    @Override
    public void showEditorAnnotation(@NotNull FileEditor editor, @NotNull JComponent annotationComoponent) {
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
  }

  public static class MyVirtualFile extends VirtualFile {

    public boolean myValid = true;

    @Override
    @NotNull
    public VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getPath() {
      return null;
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
      return new VirtualFile[0];
    }

    @Override
    public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
      return null;
    }

    @Override
    public VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
      return null;
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

    @Override
    public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor) {
      return null;
    }

    @Override
    public ToolWindow registerToolWindow(@NotNull String id,
                                         @NotNull JComponent component,
                                         @NotNull ToolWindowAnchor anchor,
                                         Disposable parentDisposable,
                                         boolean canWorkInDumbMode, boolean canCloseContents) {
      return null;
    }

    @Override
    public ToolWindow registerToolWindow(@NotNull String id,
                                         @NotNull JComponent component,
                                         @NotNull ToolWindowAnchor anchor,
                                         Disposable parentDisposable,
                                         boolean canWorkInDumbMode) {
      return null;
    }

    @Override
    public ToolWindow registerToolWindow(@NotNull String id, @NotNull JComponent component, @NotNull ToolWindowAnchor anchor, Disposable parentDisposable) {
      return null;
    }

    @Override
    public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor) {
      return null;
    }

    @Override
    public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor,
                                         final Disposable parentDisposable, final boolean dumbAware) {
      return null;
    }

    @Override
    public ToolWindow registerToolWindow(@NotNull final String id, final boolean canCloseContent, @NotNull final ToolWindowAnchor anchor, final boolean secondary) {
      return null;
    }

    public JComponent getFocusTargetFor(final JComponent comp) {
      return null;
    }

    @Override
    public void unregisterToolWindow(@NotNull String id) {
    }

    @Override
    public void activateEditorComponent() {
    }

    public ActionCallback requestFocus(final Component c, final boolean forced) {
      return new ActionCallback.Done();
    }

    public ActionCallback requestFocus(final ActiveRunnable command, final boolean forced) {
      return new ActionCallback.Done();
    }

    @Override
    public boolean isEditorComponentActive() {
      return false;
    }

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
    public void invokeLater(Runnable runnable) {
    }

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
    public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
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
