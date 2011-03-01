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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2006
 * Time: 17:08:11
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ApplyPatchAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    showApplyPatch(project, null);
  }

  public static void showApplyPatch(final Project project, final VirtualFile file) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final VirtualFile toUse;
    if (file != null) {
      toUse = file;
    } else {
      final FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(
        ApplyPatchDifferentiatedDialog.createSelectPatchDescriptor(), project);
      final VirtualFile[] files = fileChooserDialog.choose(null, project);
      if (files.length != 1) {
        return;
      }
      toUse = files[0];
    }
    final ApplyPatchDifferentiatedDialog dialog = new ApplyPatchDifferentiatedDialog(project, new ApplyPatchDefaultExecutor(project),
      Collections.<ApplyPatchExecutor>singletonList(new ImportToShelfExecutor(project)), ApplyPatchMode.APPLY, toUse);
    dialog.show();
  }

  public static void applySkipDirs(final List<FilePatch> patches, final int skipDirs) {
    if (skipDirs < 1) {
      return;
    }
    for (FilePatch patch : patches) {
      patch.setBeforeName(skipN(patch.getBeforeName(), skipDirs));
      patch.setAfterName(skipN(patch.getAfterName(), skipDirs));
    }
  }

  private static String skipN(final String path, final int num) {
    final String[] pieces = path.split("/");
    final StringBuilder sb = new StringBuilder();
    for (int i = num; i < pieces.length; i++) {
      final String piece = pieces[i];
      sb.append('/').append(piece);
    }
    return sb.toString();
  }

  public static<T extends FilePatch> ApplyPatchStatus applyOnly(final Project project, final ApplyFilePatchBase<T> patch,
                                                                final ApplyPatchContext context, final VirtualFile file) {
    final T patchBase = patch.getPatch();
    final Application application = ApplicationManager.getApplication();
    final ApplyPatchException[] exc = new ApplyPatchException[1];
    final ApplyPatchStatus applyPatchStatus = application.runWriteAction(new Computable<ApplyPatchStatus>() {
      @Override
      public ApplyPatchStatus compute() {
        try {
          return patch.apply(file, context, project);
        }
        catch (IOException e) {
          LOG.error(e);
          return ApplyPatchStatus.FAILURE;
        }
        catch (ApplyPatchException e) {
          exc[0] = e;
        }
        return ApplyPatchStatus.FAILURE;
      }
    });
    if (exc[0] != null) {
      if (! patchBase.isNewFile() && ! patchBase.isDeletedFile() && patchBase instanceof TextFilePatch) {
        //final VirtualFile beforeRename = (pathBeforeRename == null) ? file : pathBeforeRename;
        ApplyPatchStatus mergeStatus = mergeAgainstBaseVersion(project, file, new FilePathImpl(file), (TextFilePatch) patchBase,
                                                               ApplyPatchMergeRequestFactory.INSTANCE);
        if (mergeStatus != null) {
          return mergeStatus;
        }
      }
      Messages.showErrorDialog(project, VcsBundle.message("patch.apply.error", patchBase.getBeforeName(), exc[0].getMessage()),
                               VcsBundle.message("patch.apply.dialog.title"));
      return ApplyPatchStatus.FAILURE;
    }
    return applyPatchStatus;
  }

  @Nullable
  public static ApplyPatchStatus mergeAgainstBaseVersion(final Project project, final VirtualFile file, final FilePath pathBeforeRename,
                                                         final TextFilePatch patch, final PatchMergeRequestFactory mergeRequestFactory) {
    final ApplyPatchForBaseRevisionTexts threeTexts = ApplyPatchForBaseRevisionTexts.create(project, file, pathBeforeRename, patch);
    if ((threeTexts == null) || (threeTexts.getStatus() == null)) {
      return null;
    }
    ApplyPatchStatus status = threeTexts.getStatus();
    if (ApplyPatchStatus.FAILURE.equals(status)) {
      final VcsException vcsExc = threeTexts.getException();
      Messages.showErrorDialog(project, VcsBundle.message("patch.load.base.revision.error", patch.getBeforeName(),
                                                          vcsExc == null ? null : vcsExc.getMessage()), VcsBundle.message("patch.apply.dialog.title"));
      return status;
    }
    if (status != ApplyPatchStatus.ALREADY_APPLIED) {
      return ApplicationManager.getApplication().runWriteAction(new Computable<ApplyPatchStatus>() {
        @Override
        public ApplyPatchStatus compute() {
          return showMergeDialog(project, file, threeTexts.getBase(), threeTexts.getPatched(), mergeRequestFactory);
        }
      });
    }
    else {
      return status;
    }
  }

  private static ApplyPatchStatus showMergeDialog(Project project, VirtualFile file, CharSequence content, final String patchedContent,
                                                  final PatchMergeRequestFactory mergeRequestFactory) {
    CharSequence fileContent = LoadTextUtil.loadText(file);
    if (fileContent == null || content == null) {
      return ApplyPatchStatus.FAILURE;
    }
    final MergeRequest request = mergeRequestFactory.createMergeRequest(fileContent.toString(), patchedContent, content.toString(), file,
                                                                        project);
    DiffManager.getInstance().getDiffTool().show(request);
    if (request.getResult() == DialogWrapper.OK_EXIT_CODE) {
      return ApplyPatchStatus.SUCCESS;
    }
    request.restoreOriginalContent();
    return ApplyPatchStatus.FAILURE;
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP)) {
      VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      e.getPresentation().setVisible(project != null && vFile != null && vFile.getFileType() == StdFileTypes.PATCH);
    }
    else {
      e.getPresentation().setEnabled(project != null);
    }
  }

  public static class ApplyPatchMergeRequestFactory implements PatchMergeRequestFactory {
    private final boolean myReadOnly;

    public static final ApplyPatchMergeRequestFactory INSTANCE = new ApplyPatchMergeRequestFactory(false);
    public static final ApplyPatchMergeRequestFactory INSTANCE_READ_ONLY = new ApplyPatchMergeRequestFactory(true);

    public ApplyPatchMergeRequestFactory(final boolean readOnly) {
      myReadOnly = readOnly;
    }

    public MergeRequest createMergeRequest(final String leftText, final String rightText, final String originalContent, @NotNull final VirtualFile file,
                                           final Project project) {
      MergeRequest request;
      if (myReadOnly) {
        request = DiffRequestFactory.getInstance()
          .create3WayDiffRequest(leftText, rightText, originalContent, project, null, null);
      } else {
        request = DiffRequestFactory.getInstance().createMergeRequest(leftText, rightText, originalContent,
                                                                      file, project, ActionButtonPresentation.APPLY,
                                                                      ActionButtonPresentation.CANCEL_WITH_PROMPT);
      }

      request.setVersionTitles(new String[] {
        VcsBundle.message("patch.apply.conflict.local.version"),
        VcsBundle.message("patch.apply.conflict.merged.version"),
        VcsBundle.message("patch.apply.conflict.patched.version")
      });
      request.setWindowTitle(VcsBundle.message("patch.apply.conflict.title", file.getPresentableUrl()));
      return request;
    }
  }
}
