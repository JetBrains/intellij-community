// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.vcs.CompareWithLocalDialog;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE;
import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE;
import static com.intellij.vcsUtil.VcsUtil.getShortRevisionString;

public final class VcsDiffUtil {
  @Nls
  @NotNull
  public static String getRevisionTitle(@NotNull @NlsSafe String revision, boolean localMark) {
    return revision +
           (localMark ? " (" + VcsBundle.message("diff.title.local") + ")" : "");
  }

  @Nls
  @NotNull
  public static String getRevisionTitle(@NotNull @NlsSafe String revision, @Nullable FilePath file, @Nullable FilePath baseFile) {
    boolean needFileName = file != null && !VcsFileUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(baseFile, file);
    if (!needFileName) return revision;

    String fileName = getRelativeFileName(baseFile, file);

    return revision.isEmpty() ? fileName : String.format("%s (%s)", revision, fileName); //NON-NLS
  }

  public static void putFilePathsIntoChangeContext(@NotNull Change change, @NotNull Map<Key<?>, Object> context) {
    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();
    FilePath aFile = afterRevision == null ? null : afterRevision.getFile();
    FilePath bFile = beforeRevision == null ? null : beforeRevision.getFile();
    String afterRevisionName = afterRevision instanceof CurrentContentRevision
                               ? VcsBundle.message("diff.title.local")
                               : getShortHash(afterRevision);
    context.put(VCS_DIFF_RIGHT_CONTENT_TITLE, getRevisionTitle(afterRevisionName, aFile, null));
    context.put(VCS_DIFF_LEFT_CONTENT_TITLE, getRevisionTitle(beforeRevision, bFile, aFile));
  }

  @Nls
  @NotNull
  public static String getRevisionTitle(@Nullable ContentRevision revision,
                                        @Nullable FilePath file,
                                        @Nullable FilePath baseFile) {
    return getRevisionTitle(getShortHash(revision), file, baseFile);
  }

  @NlsSafe
  @NotNull
  private static String getShortHash(@Nullable ContentRevision revision) {
    if (revision == null) return "";
    VcsRevisionNumber revisionNumber = revision.getRevisionNumber();
    if (revisionNumber instanceof ShortVcsRevisionNumber) return ((ShortVcsRevisionNumber)revisionNumber).toShortString();
    return revisionNumber.asString();
  }

  @NlsSafe
  @NotNull
  private static String getRelativeFileName(@Nullable FilePath baseFile, @NotNull FilePath file) {
    if (baseFile == null || !baseFile.getName().equals(file.getName())) return file.getName();
    FilePath aParentPath = baseFile.getParentPath();
    if (aParentPath == null) return file.getName();
    return VcsFileUtil.relativePath(aParentPath.getIOFile(), file.getIOFile());
  }

  @RequiresEdt
  public static void showChangesDialog(@NotNull Project project,
                                       @NotNull @NlsContexts.DialogTitle String title,
                                       @NotNull List<? extends Change> changes) {
    DialogBuilder dialogBuilder = new DialogBuilder(project);

    dialogBuilder.setTitle(title);
    dialogBuilder.setActionDescriptors(new DialogBuilder.CloseDialogAction());
    final SimpleChangesBrowser changesBrowser = new SimpleChangesBrowser(project, false, true);
    changesBrowser.setChangesToDisplay(changes);
    dialogBuilder.setCenterPanel(changesBrowser);
    dialogBuilder.setPreferredFocusComponent(changesBrowser.getPreferredFocusedComponent());
    dialogBuilder.setDimensionServiceKey("VcsDiffUtil.ChangesDialog");
    dialogBuilder.showNotModal();
  }

  @NotNull
  public static List<Change> createChangesWithCurrentContentForFile(@NotNull FilePath filePath,
                                                                    @Nullable ContentRevision beforeContentRevision) {
    return Collections.singletonList(new Change(beforeContentRevision, CurrentContentRevision.create(filePath)));
  }

  public static void showChangesWithWorkingDirLater(@NotNull final Project project,
                                                    @NotNull final VirtualFile file,
                                                    @NotNull final VcsRevisionNumber targetRevNumber,
                                                    @NotNull DiffProvider provider) {
    String revNumTitle1 = getRevisionTitle(getShortRevisionString(targetRevNumber), false);
    String revNumTitle2 = VcsBundle.message("diff.title.local");
    String dialogTitle = VcsBundle.message("history.dialog.title.difference.between.versions.in",
                                           revNumTitle1, revNumTitle2, VcsUtil.getFilePath(file));

    CompareWithLocalDialog.showChanges(project, dialogTitle, CompareWithLocalDialog.LocalContent.AFTER, () -> {
      return provider.compareWithWorkingDir(file, targetRevNumber);
    });
  }
}
