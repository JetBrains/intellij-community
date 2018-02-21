// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class FileEditorManagerEx extends FileEditorManager implements BusyObject {
  private final List<EditorDataProvider> myDataProviders = new ArrayList<>();

  public static FileEditorManagerEx getInstanceEx(@NotNull Project project) {
    return (FileEditorManagerEx)getInstance(project);
  }

  /**
   * @return {@code JComponent} which represent the place where all editors are located
   */
  public abstract JComponent getComponent();

  /**
   * @return preferred focused component inside myEditor tabbed container.
   * This method does similar things like {@link FileEditor#getPreferredFocusedComponent()}
   * but it also tracks (and remember) focus movement inside tabbed container.
   *
   * @see EditorComposite#getPreferredFocusedComponent()
   */
  @Nullable
  public abstract JComponent getPreferredFocusedComponent();

  @NotNull
  public abstract Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file);

  @Nullable
  public abstract VirtualFile getFile(@NotNull FileEditor editor);

  /**
   * Refreshes the text, colors and icon of the editor tabs representing the specified file.
   *
   * @param file the file to refresh.
   */
  public abstract void updateFilePresentation(@NotNull VirtualFile file);

  /**
   * Synchronous version of {@link #getActiveWindow()}. Will return {@code null} if invoked not from EDT.
   * @return current window in splitters
   */
  public abstract EditorWindow getCurrentWindow();

  /**
   * Asynchronous version of {@link #getCurrentWindow()}. Execution happens after focus settle down. Can be invoked on any thread.
   */
  @NotNull
  public abstract Promise<EditorWindow> getActiveWindow();

  public abstract void setCurrentWindow(EditorWindow window);

  /**
   * Closes editors for the file opened in particular window.
   *
   * @param file file to be closed. Cannot be null.
   */
  public abstract void closeFile(@NotNull VirtualFile file, @NotNull EditorWindow window);

  public abstract void unsplitWindow();

  public abstract void unsplitAllWindow();

  public abstract int getWindowSplitCount();

  public abstract boolean hasSplitOrUndockedWindows();

  @NotNull
  public abstract EditorWindow[] getWindows();

  /**
   * @return arrays of all files (including {@code file} itself) that belong
   * to the same tabbed container. The method returns empty array if {@code file}
   * is not open. The returned files have the same order as they have in the
   * tabbed container.
   */
  @NotNull
  public abstract VirtualFile[] getSiblings(@NotNull VirtualFile file);

  public abstract void createSplitter(int orientation, @Nullable EditorWindow window);

  public abstract void changeSplitterOrientation();

  public abstract void flipTabs();
  public abstract boolean tabsMode();

  public abstract boolean isInSplitter();

  public abstract boolean hasOpenedFile ();

  @Nullable
  public abstract VirtualFile getCurrentFile();

  @Nullable
  public abstract Pair <FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file);

  /**
   * Closes all files IN ACTIVE SPLITTER (window).
   *
   * @see com.intellij.ui.docking.DockManager#getContainers()
   * @see com.intellij.ui.docking.DockContainer#closeAll()
   */
  public abstract void closeAllFiles();

  @NotNull
  public abstract EditorsSplitters getSplitters();

  @Override
  @NotNull
  public FileEditor[] openFile(@NotNull final VirtualFile file, final boolean focusEditor) {
    return openFileWithProviders(file, focusEditor, false).getFirst ();
  }

  @NotNull
  @Override
  public FileEditor[] openFile(@NotNull VirtualFile file, boolean focusEditor, boolean searchForOpen) {
    return openFileWithProviders(file, focusEditor, searchForOpen).getFirst();
  }

  @NotNull
  public abstract Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                boolean focusEditor,
                                                                                boolean searchForSplitter);

  @NotNull
  public abstract Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                boolean focusEditor,
                                                                                @NotNull EditorWindow window);

  public abstract boolean isChanged(@NotNull EditorComposite editor);

  public abstract EditorWindow getNextWindow(@NotNull final EditorWindow window);

  public abstract EditorWindow getPrevWindow(@NotNull final EditorWindow window);

  public abstract boolean isInsideChange();

  @Override
  @Nullable
  public final Object getData(@NotNull String dataId, @NotNull Editor editor, @NotNull Caret caret) {
    for (final EditorDataProvider dataProvider : myDataProviders) {
      final Object o = dataProvider.getData(dataId, editor, caret);
      if (o != null) return o;
    }
    return null;
  }

  @Override
  public void registerExtraEditorDataProvider(@NotNull final EditorDataProvider provider, Disposable parentDisposable) {
    myDataProviders.add(provider);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> myDataProviders.remove(provider));
    }
  }

  public void refreshIcons() {
    if (this instanceof FileEditorManagerImpl) {
      final FileEditorManagerImpl mgr = (FileEditorManagerImpl)this;
      Set<EditorsSplitters> splitters = mgr.getAllSplitters();
      for (EditorsSplitters each : splitters) {
        for (VirtualFile file : mgr.getOpenFiles()) {
          each.updateFileIcon(file);
        }
      }
    }
  }

  public abstract EditorsSplitters getSplittersFor(Component c);


  @NotNull
  public abstract ActionCallback notifyPublisher(@NotNull Runnable runnable);

  @Override
  public void runWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable) {
    AsyncEditorLoader.performWhenLoaded(editor, runnable);
  }
}
