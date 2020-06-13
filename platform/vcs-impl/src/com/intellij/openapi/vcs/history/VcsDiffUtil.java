// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE;
import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE;
import static com.intellij.vcsUtil.VcsUtil.getShortRevisionString;

public final class VcsDiffUtil {
  @CalledInAwt
  public static void showDiffFor(@NotNull Project project,
                                 @NotNull Collection<? extends Change> changes,
                                 @NotNull String revNumTitle1,
                                 @NotNull String revNumTitle2,
                                 @NotNull FilePath filePath) {
    if (filePath.isDirectory()) {
      showChangesDialog(project, getDialogTitle(filePath, revNumTitle1, revNumTitle2), new ArrayList<>(changes));
    }
    else {
      if (changes.isEmpty()) {
        DiffManager.getInstance().showDiff(project, new MessageDiffRequest("No Changes Found"));
      }
      else {
        Map<Key<?>, Object> revTitlesMap = new HashMap<>(2);
        revTitlesMap.put(VCS_DIFF_LEFT_CONTENT_TITLE, revNumTitle1);
        revTitlesMap.put(VCS_DIFF_RIGHT_CONTENT_TITLE, revNumTitle2);
        ShowDiffContext showDiffContext = new ShowDiffContext() {
          @NotNull
          @Override
          public Map<Key<?>, Object> getChangeContext(@NotNull Change change) {
            return revTitlesMap;
          }
        };
        ShowDiffAction.showDiffForChange(project, changes, 0, showDiffContext);
      }
    }
  }

  @NotNull
  private static String getDialogTitle(@NotNull final FilePath filePath, @NotNull final String revNumTitle1,
                                       @NotNull final String revNumTitle2) {
    return String.format("Difference between %s and %s versions in %s", revNumTitle1, revNumTitle2, filePath.getName());
  }

  @NotNull
  public static String getRevisionTitle(@NotNull String revision, boolean localMark) {
    return revision +
           (localMark ? " (" + VcsBundle.message("diff.title.local") + ")" : "");
  }

  public static void putFilePathsIntoChangeContext(@NotNull Change change, @NotNull Map<Key<?>, Object> context) {
    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();
    FilePath aFile = afterRevision == null ? null : afterRevision.getFile();
    FilePath bFile = beforeRevision == null ? null : beforeRevision.getFile();
    context.put(VCS_DIFF_RIGHT_CONTENT_TITLE, getRevisionTitle(afterRevision, aFile, null));
    context.put(VCS_DIFF_LEFT_CONTENT_TITLE, getRevisionTitle(beforeRevision, bFile, aFile));
  }

  @NotNull
  public static String getRevisionTitle(@Nullable ContentRevision revision,
                                        @Nullable FilePath file,
                                        @Nullable FilePath baseFile) {
    return getShortHash(revision) +
           (file == null || VcsFileUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(baseFile, file)
            ? ""
            : " (" + getRelativeFileName(baseFile, file) + ")");
  }

  @NotNull
  private static String getShortHash(@Nullable ContentRevision revision) {
    if (revision == null) return "";
    VcsRevisionNumber revisionNumber = revision.getRevisionNumber();
    if (revisionNumber instanceof ShortVcsRevisionNumber) return ((ShortVcsRevisionNumber)revisionNumber).toShortString();
    return revisionNumber.asString();
  }

  @NotNull
  private static String getRelativeFileName(@Nullable FilePath baseFile, @NotNull FilePath file) {
    if (baseFile == null || !baseFile.getName().equals(file.getName())) return file.getName();
    FilePath aParentPath = baseFile.getParentPath();
    if (aParentPath == null) return file.getName();
    return VcsFileUtil.relativePath(aParentPath.getIOFile(), file.getIOFile());
  }

  @CalledInAwt
  public static void showChangesDialog(@NotNull Project project, @NotNull String title, @NotNull List<? extends Change> changes) {
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

    BackgroundableActionLock lock = BackgroundableActionLock.getLock(project, VcsBackgroundableActions.COMPARE_WITH, file);

    final Task.Backgroundable task = new Task.Backgroundable(project, VcsBundle.message("file.history.diff.with.local.process"), true) {
      private Collection<Change> changes;
      private VcsRevisionNumber currentRevNumber;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          changes = provider.compareWithWorkingDir(file, targetRevNumber);
          currentRevNumber = provider.getCurrentRevision(file);
        }
        catch (VcsException e) {
          String title = String.format("Compare with %s failed", getShortRevisionString(targetRevNumber));
          String message = String.format("Couldn't compare %s with revision [%s];\n %s",
                                         file, getShortRevisionString(targetRevNumber), e.getMessage());
          VcsNotifier.getInstance(project).notifyError(title, message);
        }
      }

      @Override
      public void onSuccess() {
        //if changes null -> then exception occurred before
        if (changes != null) {
          String currentRevTitle = currentRevNumber != null
                                   ? getRevisionTitle(getShortRevisionString(currentRevNumber), true)
                                   : VcsBundle.message("diff.title.local");
          showDiffFor(
            project,
            changes,
            getRevisionTitle(getShortRevisionString(targetRevNumber), false),
            currentRevTitle,
            VcsUtil.getFilePath(file)
          );
        }
      }

      @Override
      public void onFinished() {
        lock.unlock();
      }
    };

    lock.lock();
    ProgressManager.getInstance().run(task);
  }
}
