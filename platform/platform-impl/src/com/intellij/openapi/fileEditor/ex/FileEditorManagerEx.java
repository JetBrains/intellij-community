// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.*;
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
  public abstract @Nullable JComponent getPreferredFocusedComponent();

  public abstract @NotNull Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file);

  /** @deprecated use {@link FileEditor#getFile()} instead */
  @Deprecated(forRemoval = true)
  public abstract @Nullable VirtualFile getFile(@NotNull FileEditor editor);

  /**
   * Synchronous version of {@link #getActiveWindow()}. Will return {@code null} if invoked not from EDT.
   * @return current window in splitters
   */
  public abstract EditorWindow getCurrentWindow();

  /**
   * Asynchronous version of {@link #getCurrentWindow()}. Execution happens after focus settle down. Can be invoked on any thread.
   */
  public abstract @NotNull Promise<EditorWindow> getActiveWindow();

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

  public abstract EditorWindow @NotNull [] getWindows();

  /**
   * @return arrays of all files (including {@code file} itself) that belong
   * to the same tabbed container. The method returns empty array if {@code file}
   * is not open. The returned files have the same order as they have in the
   * tabbed container.
   */
  public abstract VirtualFile @NotNull [] getSiblings(@NotNull VirtualFile file);

  public abstract void createSplitter(int orientation, @Nullable EditorWindow window);

  public abstract void changeSplitterOrientation();

  public abstract boolean isInSplitter();

  public abstract boolean hasOpenedFile();

  public boolean canOpenFile(@NotNull VirtualFile file) {
    return FileEditorProviderManager.getInstance().getProviders(getProject(), file).length > 0;
  }

  public abstract @Nullable VirtualFile getCurrentFile();

  public abstract @Nullable FileEditorWithProvider getSelectedEditorWithProvider(@NotNull VirtualFile file);

  /**
   * Closes all files IN ACTIVE SPLITTER (window).
   *
   * @see com.intellij.ui.docking.DockManager#getContainers()
   * @see com.intellij.ui.docking.DockContainer#closeAll()
   */
  public abstract void closeAllFiles();

  public abstract @NotNull EditorsSplitters getSplitters();

  @Override
  public final FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor) {
    return openFileWithProviders(file, focusEditor, false).getFirst();
  }

  @Override
  public final FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor, boolean searchForOpen) {
    return openFileWithProviders(file, focusEditor, searchForOpen).getFirst();
  }

  public abstract @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                          boolean focusEditor,
                                                                                          boolean searchForSplitter);

  public abstract @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                          boolean focusEditor,
                                                                                          @NotNull EditorWindow window);

  public @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                 @Nullable EditorWindow window,
                                                                                 @NotNull FileEditorOpenOptions options) {
    return window != null && !window.isDisposed() ? openFileWithProviders(file, options.getRequestFocus(), window)
                                                  : openFileWithProviders(file, options.getRequestFocus(), options.getReuseOpen());
  }

  public abstract boolean isChanged(@NotNull EditorComposite editor);

  public abstract EditorWindow getNextWindow(final @NotNull EditorWindow window);

  public abstract EditorWindow getPrevWindow(final @NotNull EditorWindow window);

  public abstract boolean isInsideChange();

  @Override
  public final @Nullable Object getData(@NotNull String dataId, @NotNull Editor editor, @NotNull Caret caret) {
    for (final EditorDataProvider dataProvider : myDataProviders) {
      final Object o = dataProvider.getData(dataId, editor, caret);
      if (o != null) return o;
    }
    return null;
  }

  @Override
  public void registerExtraEditorDataProvider(final @NotNull EditorDataProvider provider, Disposable parentDisposable) {
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


  public abstract @NotNull ActionCallback notifyPublisher(@NotNull Runnable runnable);

  @Override
  public void runWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable) {
    AsyncEditorLoader.performWhenLoaded(editor, runnable);
  }
}
