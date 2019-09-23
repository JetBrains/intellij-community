// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.openapi.progress.ProgressIndicator;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE;
import static com.intellij.diff.util.DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE;

public class VcsDiffUtil {

  @CalledInAwt
  public static void showDiffFor(@NotNull Project project,
                                 @NotNull final Collection<? extends Change> changes,
                                 @NotNull final String revNumTitle1,
                                 @NotNull final String revNumTitle2,
                                 @NotNull final FilePath filePath) {
    if (filePath.isDirectory()) {
      showChangesDialog(project, getDialogTitle(filePath, revNumTitle1, revNumTitle2), new ArrayList<>(changes));
    }
    else {
      if (changes.isEmpty()) {
        DiffManager.getInstance().showDiff(project, new MessageDiffRequest("No Changes Found"));
      }
      else {
        final HashMap<Key, Object> revTitlesMap = new HashMap<>(2);
        revTitlesMap.put(VCS_DIFF_LEFT_CONTENT_TITLE, revNumTitle1);
        revTitlesMap.put(VCS_DIFF_RIGHT_CONTENT_TITLE, revNumTitle2);
        ShowDiffContext showDiffContext = new ShowDiffContext() {
          @NotNull
          @Override
          public Map<Key, Object> getChangeContext(@NotNull Change change) {
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

  @FunctionalInterface
  public interface ChangesProducer<R extends VcsRevisionNumber> {
    @Nullable
    Collection<Change> produce(@NotNull Project project, @NotNull VirtualFile file, @NotNull R revision) throws VcsException;
  }

  /**
   * Shows
   *  - diff if \p file is a file
   *  - changes if \p file is a directory
   * after collecting changes in background with modal progress.
   *
   * @param project
   * @param file
   * @param headRevision
   * @param targetRevision
   * @param changesProducer
   * @param <R>
   */
  public static <R extends VcsRevisionNumber>
  void showDiffWithRevisionUnderModalProgress(@NotNull final Project project,
                                              @NotNull final VirtualFile file,
                                              @NotNull final R headRevision,
                                              @NotNull final R targetRevision,
                                              @NotNull ChangesProducer<R> changesProducer) {

    new Task.Backgroundable(project, "Collecting Changes...", true) {
      private Collection<Change> changes;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          changes = changesProducer.produce(project, file, targetRevision);
        }
        catch (VcsException e) {
          VcsNotifier.getInstance(project).notifyImportantWarning("Couldn't compare with revision", String
            .format("Couldn't compare folder [%s] with revision [%s];\n %s", file, targetRevision.asString(), e.getMessage()));
        }
      }

      @Override
      public void onSuccess() {
        //if changes null -> then exception occurred before
        if (changes != null) {
          showDiffFor(
            project,
            changes,
            getRevisionTitle(getRevisionPresentation(targetRevision), false),
            getRevisionTitle(getRevisionPresentation(headRevision), true),
            VcsUtil.getFilePath(file)
          );
        }
      }
    }.queue();
  }

  @NotNull
  private static String getRevisionPresentation(@NotNull VcsRevisionNumber revisionNumber) {
    return revisionNumber instanceof ShortVcsRevisionNumber
           ? ((ShortVcsRevisionNumber)revisionNumber).toShortString()
           : revisionNumber.asString();
  }
}
