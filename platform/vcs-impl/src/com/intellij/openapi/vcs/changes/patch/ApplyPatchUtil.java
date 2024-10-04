// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.diff.DiffManager;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatch;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.PATCH_APPLY_CANNOT_FIND_PATCH_FILE;
import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.PATCH_APPLY_NOT_PATCH_FILE;
import static com.intellij.openapi.vcs.changes.patch.PatchFileType.isPatchFile;

@ApiStatus.Internal
public final class ApplyPatchUtil {
  private static final Logger LOG = Logger.getInstance(ApplyPatchUtil.class);

  public static void showApplyPatch(@NotNull Project project, @NotNull VirtualFile file) {
    final ApplyPatchDifferentiatedDialog dialog = new ApplyPatchDifferentiatedDialog(
      project, new ApplyPatchDefaultExecutor(project),
      Collections.singletonList(new ImportToShelfExecutor(project)), ApplyPatchMode.APPLY, file);
    dialog.show();
  }

  @RequiresEdt
  public static Boolean showAndGetApplyPatch(@NotNull Project project, @NotNull File file) {
    VirtualFile vFile = VfsUtil.findFileByIoFile(file, true);
    String patchPath = file.getPath();
    if (vFile == null) {
      VcsNotifier.getInstance(project).notifyWeakError(PATCH_APPLY_CANNOT_FIND_PATCH_FILE,
                                                       VcsBundle.message("patch.apply.can.t.find.patch.file.warning", HtmlChunk.text(patchPath)));
      return false;
    }
    if (!isPatchFile(vFile)) {
      VcsNotifier.getInstance(project).notifyWeakError(PATCH_APPLY_NOT_PATCH_FILE,
                                                       VcsBundle.message("patch.apply.not.patch.type.file.error", HtmlChunk.text(vFile.getPath())));
      return false;
    }

    return showAndGetApplyPatch(project, new ApplyPatchFile(vFile));
  }

  @RequiresBackgroundThread
  public static @Nullable ApplyPatchFile getPatchFile(@NotNull File file) {
    VirtualFile vFile = VfsUtil.findFileByIoFile(file, true);
    if (vFile == null || !isPatchFile(vFile)) {
      return null;
    }

    return new ApplyPatchFile(vFile);
  }

  @RequiresEdt
  public static @NotNull Boolean showAndGetApplyPatch(@NotNull Project project, @NotNull ApplyPatchFile patchFile) {
    var dialog = new ApplyPatchDifferentiatedDialog(project,
                                                    new ApplyPatchDefaultExecutor(project),
                                                    Collections.emptyList(),
                                                    ApplyPatchMode.APPLY_PATCH_IN_MEMORY, patchFile.getFile());
    dialog.setModal(true);
    return dialog.showAndGet();
  }

  public static @NotNull ApplyPatchStatus applyContent(@NotNull Project project,
                                                       @NotNull ApplyFilePatchBase<?> patch,
                                                       @Nullable ApplyPatchContext context,
                                                       @NotNull VirtualFile file,
                                                       @Nullable CommitContext commitContext,
                                                       boolean reverse,
                                                       @Nullable String leftPanelTitle,
                                                       @Nullable String rightPanelTitle) {
    ApplyFilePatch.Result result = tryApplyPatch(project, patch, context, file, commitContext);

    ApplyPatchStatus status = result.getStatus();
    if (ApplyPatchStatus.ALREADY_APPLIED.equals(status) || ApplyPatchStatus.SUCCESS.equals(status)) {
      return status;
    }

    ApplyPatchForBaseRevisionTexts mergeData = result.getMergeData();
    if (mergeData == null) {
      return status;
    }

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      return ApplyPatchStatus.FAILURE;
    }

    if (mergeData.getBase() == null && ApplyPatchStatus.PARTIAL.equals(status)) {
      WriteAction.run(() -> {
        VcsFacade.getInstance().runHeavyModificationTask(project, document, () -> document.setText(mergeData.getPatched()));
        FileDocumentManager.getInstance().saveDocument(document);
      });
      return status;
    }

    String baseContent = convertLineSeparators(mergeData.getBase());
    String localContent = convertLineSeparators(mergeData.getLocal());
    String patchedContent = mergeData.getPatched();

    if (localContent.equals(patchedContent)) {
      return ApplyPatchStatus.ALREADY_APPLIED;
    }

    Ref<ApplyPatchStatus> applyPatchStatusReference = new Ref<>();
    Consumer<MergeResult> callback = result13 -> {
      FileDocumentManager.getInstance().saveDocument(document);
      applyPatchStatusReference.setIfNull(result13 != MergeResult.CANCEL ? ApplyPatchStatus.SUCCESS : ApplyPatchStatus.FAILURE);
    };

    try {
      MergeRequest request;
      if (baseContent != null) {
        if (reverse) {
          if (leftPanelTitle == null) leftPanelTitle = VcsBundle.message("patch.apply.conflict.patched.version");
          if (rightPanelTitle == null) rightPanelTitle = VcsBundle.message("patch.apply.conflict.local.version");

          List<String> contents = Arrays.asList(patchedContent, baseContent, localContent);
          List<String> titles = Arrays.asList(leftPanelTitle, null, rightPanelTitle);

          request = PatchDiffRequestFactory
            .createMergeRequest(project, document, file, contents, null, titles, callback);
        }
        else {
          request = PatchDiffRequestFactory
            .createMergeRequest(project, document, file, baseContent, localContent, patchedContent, callback);
        }
      }
      else {
        TextFilePatch textPatch = (TextFilePatch)patch.getPatch();
        GenericPatchApplier applier = new GenericPatchApplier(localContent, textPatch.getHunks());
        applier.execute();

        final AppliedTextPatch appliedTextPatch = AppliedTextPatch.create(applier.getAppliedInfo());
        request = PatchDiffRequestFactory.createBadMergeRequest(project, document, file, localContent, appliedTextPatch, callback);
      }
      request.putUserData(DiffUserDataKeysEx.MERGE_ACTION_CAPTIONS, result12 -> result12.equals(MergeResult.CANCEL) ? VcsBundle
        .message("patch.apply.abort.action") : null);
      request.putUserData(DiffUserDataKeysEx.MERGE_CANCEL_HANDLER, viewer -> {
        String message = VcsBundle.message("patch.apply.abort.and.rollback.prompt");
        String title = VcsBundle.message("patch.apply.abort.title");
        String yesText = VcsBundle.message("patch.apply.abort.and.rollback.action");
        String noText = VcsBundle.message("patch.apply.skip.action");
        String cancelText = VcsBundle.message("patch.apply.continue.resolve.action");
        int result1 = Messages.showYesNoCancelDialog(viewer.getComponent().getRootPane(), message, title, yesText, noText, cancelText,
                                                     Messages.getQuestionIcon());
        if (result1 == Messages.YES) {
          applyPatchStatusReference.set(ApplyPatchStatus.ABORT);
        }
        else if (result1 == Messages.NO) {
          applyPatchStatusReference.set(ApplyPatchStatus.SKIP);
        }
        return result1 != Messages.CANCEL;
      });
      DiffManager.getInstance().showMerge(project, request);
      return applyPatchStatusReference.get();
    }
    catch (InvalidDiffRequestException e) {
      LOG.warn(e);
      return ApplyPatchStatus.FAILURE;
    }
  }

  private static @NotNull ApplyFilePatch.Result tryApplyPatch(@NotNull Project project,
                                                              @NotNull ApplyFilePatchBase<?> patch,
                                                              @Nullable ApplyPatchContext context,
                                                              @NotNull VirtualFile file,
                                                              @Nullable CommitContext commitContext) {
    FilePatch patchBase = patch.getPatch();
    return WriteAction.compute(() -> {
      try {
        return patch.apply(file, context, project, VcsUtil.getFilePath(file), () -> {
          String path = ObjectUtils.chooseNotNull(patchBase.getBeforeName(), patchBase.getAfterName());
          return BaseRevisionTextPatchEP.getBaseContent(project, path, commitContext);
        }, commitContext);
      }
      catch (IOException e) {
        LOG.warn(e);
        return ApplyFilePatch.FAILURE;
      }
    });
  }

  @Nullable
  private static String convertLineSeparators(@Nullable String charSequence) {
    return charSequence != null ? StringUtil.convertLineSeparators(charSequence) : null;
  }
}
