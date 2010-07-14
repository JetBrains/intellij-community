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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.apply.ApplyFilePatchBase;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class ApplyPatchAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    final Consumer<ApplyPatchDifferentiatedDialog> callback = new Consumer<ApplyPatchDifferentiatedDialog>() {
      public void consume(ApplyPatchDifferentiatedDialog newDia) {
        if (newDia.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
          return;
        }

        final Collection<FilePatchInProgress> included = newDia.getIncluded();
        final MultiMap<VirtualFile, FilePatchInProgress> patchGroups = new MultiMap<VirtualFile, FilePatchInProgress>();
        for (FilePatchInProgress patchInProgress : included) {
          patchGroups.putValue(patchInProgress.getBase(), patchInProgress);
        }

        final Collection<PatchApplier> appliers = new LinkedList<PatchApplier>();
        for (VirtualFile base : patchGroups.keySet()) {
          final PatchApplier patchApplier =
            new PatchApplier<BinaryFilePatch>(project, base, ObjectsConvertor.convert(patchGroups.get(base), new Convertor<FilePatchInProgress, FilePatch>() {
              public FilePatch convert(FilePatchInProgress o) {
                return o.getPatch();
              }
            }), newDia.getSelectedChangeList(), null);
          appliers.add(patchApplier);
        }
        PatchApplier.executePatchGroup(appliers);
      }
    };
    FileDocumentManager.getInstance().saveAllDocuments();
    final ApplyPatchDifferentiatedDialog dialog = new ApplyPatchDifferentiatedDialog(project, callback);
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

  public static<T extends FilePatch> ApplyPatchStatus applyOnly(final Project project, final ApplyFilePatchBase<T> patch, final ApplyPatchContext context, final VirtualFile file) {
    final T patchBase = patch.getPatch();
    try {
      return patch.apply(file, context, project);
    }
    catch(ApplyPatchException ex) {
      if (!patchBase.isNewFile() && !patchBase.isDeletedFile() && patchBase instanceof TextFilePatch) {
        //final VirtualFile beforeRename = (pathBeforeRename == null) ? file : pathBeforeRename;
        ApplyPatchStatus mergeStatus = mergeAgainstBaseVersion(project, file, new FilePathImpl(file), (TextFilePatch) patchBase,
                                                               ApplyPatchMergeRequestFactory.INSTANCE);
        if (mergeStatus != null) {
          return mergeStatus;
        }
      }
      Messages.showErrorDialog(project, VcsBundle.message("patch.apply.error", patchBase.getBeforeName(), ex.getMessage()),
                               VcsBundle.message("patch.apply.dialog.title"));
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    return ApplyPatchStatus.FAILURE;
  }

  @Nullable
  public static ApplyPatchStatus mergeAgainstBaseVersion(Project project, VirtualFile file, ApplyPatchContext context,
                                                         final TextFilePatch patch,
                                                         final PatchMergeRequestFactory mergeRequestFactory) {
    final FilePath pathBeforeRename = context.getPathBeforeRename(file);
    return mergeAgainstBaseVersion(project, file, pathBeforeRename, patch, mergeRequestFactory);
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
      return showMergeDialog(project, file, threeTexts.getBase(), threeTexts.getPatched(), mergeRequestFactory);
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
        request = DiffRequestFactory.getInstance().create3WayDiffRequest(leftText, rightText, originalContent, project, null);
      } else {
        request = DiffRequestFactory.getInstance().createMergeRequest(leftText, rightText, originalContent,
                                                                                 file, project, ActionButtonPresentation.createApplyButton());
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
