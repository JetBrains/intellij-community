// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.ide.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.fileEditor.OpenFileDescriptor.unfoldCurrentLine;

public class FileNavigatorImpl implements FileNavigator {
  @Override
  public boolean canNavigate(@NotNull OpenFileDescriptor descriptor) {
    VirtualFile file = descriptor.getFile();
    return file.isValid();
  }

  @Override
  public boolean canNavigateToSource(@NotNull OpenFileDescriptor descriptor) {
    VirtualFile file = descriptor.getFile();
    if (!file.isValid()) return false;

    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(descriptor.getProject());
    return fileEditorManager.canOpenFile(file) || file.getFileType() instanceof INativeFileType;
  }

  @Override
  public void navigate(@NotNull OpenFileDescriptor descriptor, boolean requestFocus) {
    if (!canNavigate(descriptor)) {
      throw new IllegalStateException("target not valid");
    }

    if (!descriptor.getFile().isDirectory()) {
      if (navigateInEditorOrNativeApp(descriptor, requestFocus)) return;
    }

    if (navigateInProjectView(descriptor.getProject(), descriptor.getFile(), requestFocus)) return;

    String message = IdeBundle.message("error.files.of.this.type.cannot.be.opened", ApplicationNamesInfo.getInstance().getProductName());
    Messages.showErrorDialog(descriptor.getProject(), message, IdeBundle.message("title.cannot.open.file"));
  }

  private boolean navigateInEditorOrNativeApp(@NotNull OpenFileDescriptor descriptor, boolean requestFocus) {
    FileType type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(descriptor.getFile(), descriptor.getProject());
    if (type == null || !descriptor.getFile().isValid()) return false;

    if (type instanceof INativeFileType) {
      return ((INativeFileType)type).openFileInAssociatedApplication(descriptor.getProject(), descriptor.getFile());
    }

    return navigateInEditor(descriptor, requestFocus);
  }

  private boolean navigateInProjectView(@NotNull Project project, @NotNull VirtualFile file, boolean requestFocus) {
    SelectInContext context = new FileSelectInContext(project, file, null);
    for (SelectInTarget target : SelectInManager.getInstance(project).getTargetList()) {
      if (context.selectIn(target, requestFocus)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean navigateInEditor(@NotNull OpenFileDescriptor descriptor, boolean requestFocus) {
    return navigateInRequestedEditor(descriptor) || navigateInAnyFileEditor(descriptor, requestFocus);
  }

  private boolean navigateInRequestedEditor(@NotNull OpenFileDescriptor descriptor) {
    @SuppressWarnings("deprecation") DataContext ctx = DataManager.getInstance().getDataContext();
    Editor e = OpenFileDescriptor.NAVIGATE_IN_EDITOR.getData(ctx);
    if (e == null) return false;
    if (e.isDisposed()) {
      Logger.getInstance(OpenFileDescriptor.class).error("Disposed editor returned for NAVIGATE_IN_EDITOR from " + ctx);
      return false;
    }
    if (!Comparing.equal(FileDocumentManager.getInstance().getFile(e.getDocument()), descriptor.getFile())) return false;

    OpenFileDescriptor.navigateInEditor(descriptor, e);
    return true;
  }

  protected boolean navigateInAnyFileEditor(@NotNull OpenFileDescriptor descriptor, boolean focusEditor) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(descriptor.getProject());
    List<FileEditor> editors = fileEditorManager.openEditor(descriptor, focusEditor);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        Editor e = ((TextEditor)editor).getEditor();
        fileEditorManager.runWhenLoaded(e, () -> unfoldCurrentLine(e));
      }
    }
    return !editors.isEmpty();
  }
}
