/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.editor.CustomFileDropHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

/**
 * @author yole
 */
public class FileDropHandler implements EditorDropHandler {
  private final Editor myEditor;

  public FileDropHandler(Editor editor) {
    myEditor = editor;
  }

  @Override
  public boolean canHandleDrop(final DataFlavor[] transferFlavors) {
    return transferFlavors != null && FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors);
  }

  @Override
  public void handleDrop(@NotNull final Transferable t, @Nullable final Project project, EditorWindow editorWindow) {
    if (project != null) {
      final List<File> fileList = FileCopyPasteUtil.getFileList(t);
      if (fileList != null) {
        boolean dropResult = ContainerUtil.process(Extensions.getExtensions(CustomFileDropHandler.CUSTOM_DROP_HANDLER_EP, project),
                                                   handler -> !(handler.canHandle(t, myEditor) && handler.handleDrop(t, myEditor, project)));
        if (!dropResult) return;

        openFiles(project, fileList, editorWindow);
      }
    }
  }

  private void openFiles(final Project project, final List<File> fileList, EditorWindow editorWindow) {
    if (editorWindow == null && myEditor != null) {
      editorWindow = findEditorWindow(project);
    }
    final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (File file : fileList) {
      final VirtualFile vFile = fileSystem.refreshAndFindFileByIoFile(file);
      final FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);
      if (vFile != null) {
        NonProjectFileWritingAccessProvider.allowWriting(vFile);
        
        if (editorWindow != null) {
          fileEditorManager.openFileWithProviders(vFile, true, editorWindow);
        }
        else {
          PsiNavigationSupport.getInstance().createNavigatable(project, vFile, -1).navigate(true);
        }
      }
    }
  }

  @Nullable
  private EditorWindow findEditorWindow(Project project) {
    final Document document = myEditor.getDocument();
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) {
      final FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);
      final EditorWindow[] windows = fileEditorManager.getWindows();
      for (EditorWindow window : windows) {
        final EditorWithProviderComposite composite = window.findFileComposite(file);
        if (composite == null) {
          continue;
        }
        for (FileEditor editor : composite.getEditors()) {
          if (editor instanceof TextEditor && ((TextEditor)editor).getEditor() == myEditor) {
            return window;
          }
        }
      }
    }
    return null;
  }
}
