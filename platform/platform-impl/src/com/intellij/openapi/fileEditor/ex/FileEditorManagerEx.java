// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex;

import com.intellij.ide.impl.DataValidators;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.docking.DockContainer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class FileEditorManagerEx extends FileEditorManager implements BusyObject {
  private final List<EditorDataProvider> myDataProviders = new ArrayList<>();

  public static FileEditorManagerEx getInstanceEx(@NotNull Project project) {
    return (FileEditorManagerEx)getInstance(project);
  }


  public static FileEditorManagerEx getInstanceExIfCreated(@NotNull Project project) {
    return (FileEditorManagerEx)project.getServiceIfCreated(FileEditorManager.class);
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
  public abstract @NotNull CompletableFuture<@Nullable EditorWindow> getActiveWindow();

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
  public abstract @NotNull Collection<VirtualFile> getSiblings(@NotNull VirtualFile file);

  public abstract void createSplitter(int orientation, @Nullable EditorWindow window);

  public abstract void changeSplitterOrientation();

  public abstract boolean isInSplitter();

  public abstract boolean hasOpenedFile();

  public boolean canOpenFile(@NotNull VirtualFile file) {
    return FileEditorProviderManager.getInstance().getProviderList(getProject(), file).size() > 0;
  }

  protected boolean canOpenFile(@NotNull VirtualFile file, @NotNull List<FileEditorProvider> providers) {
    return !providers.isEmpty();
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

  @RequiresEdt
  public @NotNull List<EditorComposite> getActiveSplittersComposites() {
    return getSplitters().getAllComposites();
  }

  @Override
  public final FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor) {
    FileEditorOpenOptions options = new FileEditorOpenOptions().withRequestFocus(focusEditor);
    return openFileWithProviders(file, null, options).getAllEditors().toArray(FileEditor.EMPTY_ARRAY);
  }

  @Override
  public final FileEditor @NotNull [] openFile(@NotNull VirtualFile file, boolean focusEditor, boolean searchForOpen) {
    FileEditorOpenOptions options = new FileEditorOpenOptions().withRequestFocus(focusEditor).withReuseOpen(searchForOpen);
    return openFileWithProviders(file, null, options).getAllEditors().toArray(FileEditor.EMPTY_ARRAY);
  }

  public abstract @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                          boolean focusEditor,
                                                                                          boolean searchForSplitter);

  public abstract @NotNull Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file,
                                                                                          boolean focusEditor,
                                                                                          @NotNull EditorWindow window);

  public @NotNull FileEditorComposite openFileWithProviders(@NotNull VirtualFile file,
                                                            @Nullable EditorWindow window,
                                                            @NotNull FileEditorOpenOptions options) {
    Pair<FileEditor[], FileEditorProvider[]> result = window == null || window.isDisposed()
                                                      ? openFileWithProviders(file, options.requestFocus, options.reuseOpen)
                                                      : openFileWithProviders(file, options.requestFocus, window);
    return FileEditorComposite.Companion.fromPair(result);
  }

  public abstract boolean isChanged(@NotNull EditorComposite editor);

  public abstract EditorWindow getNextWindow(@NotNull EditorWindow window);

  public abstract EditorWindow getPrevWindow(@NotNull EditorWindow window);

  public abstract boolean isInsideChange();

  @Override
  public final @Nullable Object getData(@NotNull String dataId, @NotNull Editor editor, @NotNull Caret caret) {
    for (final EditorDataProvider dataProvider : myDataProviders) {
      final Object o = dataProvider.getData(dataId, editor, caret);
      if (o != null) return DataValidators.validOrNull(o, dataId, dataProvider);
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
  }

  public abstract EditorsSplitters getSplittersFor(Component c);

  public abstract void notifyPublisher(@NotNull Runnable runnable);

  @Override
  public void runWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable) {
    AsyncEditorLoader.performWhenLoaded(editor, runnable);
  }

  public @Nullable DockContainer getDockContainer() {
    return null;
  }
}
