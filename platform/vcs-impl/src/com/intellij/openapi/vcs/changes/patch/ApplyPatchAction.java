// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.vcs.changes.patch.PatchFileType.isPatchFile;

public final class ApplyPatchAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (e.isFromContextMenu()) {
      VirtualFile vFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
      e.getPresentation().setEnabledAndVisible(project != null && isPatchFile(vFile));
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(project != null);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(VcsBundle.message("patch.apply.cannot.apply.now"))) return;
    FileDocumentManager.getInstance().saveAllDocuments();

    VirtualFile vFile = null;
    if (e.isFromContextMenu() || e.isFromMainMenu()) {
      vFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    }
    if (isPatchFile(vFile)) {
      showApplyPatch(project, vFile);
    }
    else {
      final FileChooserDescriptor descriptor = ApplyPatchDifferentiatedDialog.createSelectPatchDescriptor();
      final VcsApplicationSettings settings = VcsApplicationSettings.getInstance();
      final VirtualFile toSelect = settings.PATCH_STORAGE_LOCATION == null ? null :
                                   LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(settings.PATCH_STORAGE_LOCATION));

      FileChooser.chooseFile(descriptor, project, toSelect, file -> {
        final VirtualFile parent = file.getParent();
        if (parent != null) {
          settings.PATCH_STORAGE_LOCATION = parent.getPath();
        }
        showApplyPatch(project, file);
      });
    }
  }

  // used by TeamCity plugin
  public static void showApplyPatch(@NotNull Project project, @NotNull VirtualFile file) {
    ApplyPatchUtil.showApplyPatch(project, file);
  }

  /**
   * @deprecated Use {@link ApplyPatchUtil#showAndGetApplyPatch(Project, File)} instead.
   */
  @Deprecated
  @RequiresEdt
  public static Boolean showAndGetApplyPatch(@NotNull Project project, @NotNull File file) {
    return ApplyPatchUtil.showAndGetApplyPatch(project, file);
  }

  /**
   * @deprecated Use {@link ApplyPatchUtil#applyContent(Project, ApplyFilePatchBase, ApplyPatchContext, VirtualFile, CommitContext, boolean, String, String)} instead.
   */
  @ApiStatus.Internal
  @Deprecated
  public static @NotNull ApplyPatchStatus applyContent(@NotNull Project project,
                                                       @NotNull ApplyFilePatchBase<?> patch,
                                                       @Nullable ApplyPatchContext context,
                                                       @NotNull VirtualFile file,
                                                       @Nullable CommitContext commitContext,
                                                       boolean reverse,
                                                       @Nullable String leftPanelTitle,
                                                       @Nullable String rightPanelTitle) {
    return ApplyPatchUtil.applyContent(project, patch, context, file, commitContext, reverse, leftPanelTitle, rightPanelTitle);
  }
}
