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
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class FileEditorManagerEx extends FileEditorManager {
  protected final List<EditorDataProvider> myDataProviders = new ArrayList<EditorDataProvider>();

  public static FileEditorManagerEx getInstanceEx(Project project) {
    return (FileEditorManagerEx)getInstance(project);
  }

  /**
   * @return <code>JComponent</code> which represent the place where all editors are located
   */
  public abstract JComponent getComponent();

  /**
   * @return preferred focused component inside myEditor tabbed container.
   * This method does similar things like {@link FileEditor#getPreferredFocusedComponent()}
   * but it also tracks (and remember) focus movement inside tabbed container.
   *
   * @see EditorComposite#getPreferredFocusedComponent()
   */
  public abstract JComponent getPreferredFocusedComponent();

  @NotNull
  public abstract Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@NotNull VirtualFile file);

  @Nullable
  public abstract VirtualFile getFile(@NotNull FileEditor editor);

  public abstract void updateFilePresentation(VirtualFile file);

  /**
   *
   * @return current window in splitters
   */
  public abstract EditorWindow getCurrentWindow();

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

  @NotNull
  public abstract EditorWindow[] getWindows();

  /**
   * @return arrays of all files (including <code>file</code> itself) that belong
   * to the same tabbed container. The method returns empty array if <code>file</code>
   * is not open. The returned files have the same order as they have in the
   * tabbed container.
   */
  @NotNull public abstract VirtualFile[] getSiblings(VirtualFile file);

  public abstract void createSplitter(int orientation, @Nullable EditorWindow window);

  public abstract void changeSplitterOrientation();

  public abstract void flipTabs();
  public abstract boolean tabsMode();

  public abstract boolean isInSplitter();

  public abstract boolean hasOpenedFile ();

  public abstract VirtualFile getCurrentFile();

  public abstract Pair <FileEditor, FileEditorProvider> getSelectedEditorWithProvider(@NotNull VirtualFile file);

  public abstract void closeAllFiles();

  @NotNull
  public FileEditor[] openFile(@NotNull final VirtualFile file, final boolean focusEditor) {
    return openFileWithProviders(file, focusEditor).getFirst ();
  }

  @NotNull public abstract Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(@NotNull VirtualFile file, boolean focusEditor);

  public abstract boolean isChanged(@NotNull EditorComposite editor);

  public abstract EditorWindow getNextWindow(@NotNull final EditorWindow window);

  public abstract EditorWindow getPrevWindow(@NotNull final EditorWindow window);

  public abstract boolean isInsideChange();

  @Nullable
  public final Object getData(String dataId, Editor editor, final VirtualFile file) {
    for (final EditorDataProvider dataProvider : myDataProviders) {
      final Object o = dataProvider.getData(dataId, editor, file);
      if (o != null) return o;
    }
    return null;
  }

  public void registerExtraEditorDataProvider(@NotNull final EditorDataProvider provider, Disposable parentDisposable) {
    myDataProviders.add(provider);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          myDataProviders.remove(provider);
        }
      });
    }
  }
}
