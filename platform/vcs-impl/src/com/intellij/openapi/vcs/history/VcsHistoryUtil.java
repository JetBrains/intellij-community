// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.DiffVcsDataKeys;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.diff.DiffRequestFactoryImpl.DIFF_TITLE_RENAME_SEPARATOR;

public final class VcsHistoryUtil {
  private static final Logger LOG = Logger.getInstance(VcsHistoryUtil.class);

  private VcsHistoryUtil() {
  }

  public static int compare(VcsFileRevision first, VcsFileRevision second) {
    if (first instanceof CurrentRevision && second instanceof CurrentRevision) {
      return compareNumbers(first, second);
    }
    if (second instanceof CurrentRevision) return -1 * compare(second, first);

    if (first instanceof CurrentRevision) {
      int result = compareNumbers(first, second);
      if (result == 0) {
        return 1;
      }
      else {
        return result;
      }
    }
    else {
      return compareNumbers(first, second);
    }
  }

  public static int compareNumbers(VcsFileRevision first, VcsFileRevision second) {
    return first.getRevisionNumber().compareTo(second.getRevisionNumber());
  }

  /**
   * Invokes {@link com.intellij.openapi.diff.DiffManager#getDiffTool()} to show difference between the given revisions of the given file.
   *
   * @param project   project under vcs control.
   * @param path      file which revisions are compared.
   * @param revision1 first revision - 'before', to the left.
   * @param revision2 second revision - 'after', to the right.
   */
  public static void showDiff(@NotNull final Project project, @NotNull FilePath path,
                              @NotNull VcsFileRevision revision1, @NotNull VcsFileRevision revision2,
                              @NotNull @NlsContexts.Label String title1, @NotNull @NlsContexts.Label String title2) throws VcsException, IOException {
    FilePath path1 = getRevisionPath(revision1);
    FilePath path2 = getRevisionPath(revision2);

    String title;
    if (path1 != null && path2 != null) {
      title = DiffRequestFactoryImpl.getTitle(path1, path2, DIFF_TITLE_RENAME_SEPARATOR);
    }
    else {
      title = DiffRequestFactoryImpl.getContentTitle(path);
    }

    DiffContent diffContent1 = loadContentForDiff(project, path, revision1);
    DiffContent diffContent2 = loadContentForDiff(project, path, revision2);

    final DiffRequest request = new SimpleDiffRequest(title, diffContent1, diffContent2, title1, title2);

    diffContent1.putUserData(DiffVcsDataKeys.REVISION_INFO, getRevisionInfo(revision1));
    diffContent2.putUserData(DiffVcsDataKeys.REVISION_INFO, getRevisionInfo(revision2));

    WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> DiffManager.getInstance().showDiff(project, request), null, project);
  }

  @NotNull
  public static DiffContent loadContentForDiff(@NotNull Project project, @NotNull FilePath path, @NotNull VcsFileRevision revision) throws IOException, VcsException {
    return createContent(project, loadRevisionContent(revision), revision, path);
  }

  @Nullable
  private static Pair<FilePath, VcsRevisionNumber> getRevisionInfo(@NotNull VcsFileRevision revision) {
    if (revision instanceof VcsFileRevisionEx) {
      return Pair.create(((VcsFileRevisionEx)revision).getPath(), revision.getRevisionNumber());
    }
    return null;
  }

  @Nullable
  private static FilePath getRevisionPath(@NotNull VcsFileRevision revision) {
    if (revision instanceof VcsFileRevisionEx) {
      return ((VcsFileRevisionEx)revision).getPath();
    }
    return null;
  }

  public static byte @NotNull [] loadRevisionContent(@NotNull VcsFileRevision revision) throws VcsException, IOException {
    byte[] content = revision.loadContent();
    if (content == null) {
      throw new VcsException(VcsBundle.message("history.failed.to.load.content.for.revision.0",
                                               revision.getRevisionNumber().asString()));
    }
    return content;
  }

  public static String loadRevisionContentGuessEncoding(@NotNull final VcsFileRevision revision, @Nullable final VirtualFile file,
                                                        @Nullable final Project project) throws VcsException, IOException {
    final byte[] bytes = loadRevisionContent(revision);
    if (file != null) {
      return new String(bytes, file.getCharset());
    }
    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : null;
    if (e == null) {
      e = EncodingManager.getInstance();
    }

    return CharsetToolkit.bytesToString(bytes, e.getDefaultCharset());
  }

  @NotNull
  private static DiffContent createContent(@NotNull Project project, byte @NotNull [] content, @NotNull VcsFileRevision revision,
                                           @NotNull FilePath filePath) throws IOException {
    DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();
    if (isCurrent(revision)) {
      VirtualFile file = filePath.getVirtualFile();
      if (file != null) return contentFactory.create(project, file);
    }
    if (isEmpty(revision)) {
      return contentFactory.createEmpty();
    }
    return contentFactory.createFromBytes(project, content, filePath, revision.getDefaultCharset());
  }

  private static boolean isCurrent(VcsFileRevision revision) {
    return revision instanceof CurrentRevision;
  }

  public static boolean isEmpty(VcsFileRevision revision) {
    return revision == null || VcsFileRevision.NULL.equals(revision);
  }

  /**
   * Shows difference between two revisions of a file in a diff tool.
   * The content of revisions is queried in a background thread.
   *
   * @see #showDiff(Project, FilePath, VcsFileRevision, VcsFileRevision, String, String)
   */
  public static void showDifferencesInBackground(@NotNull final Project project,
                                                 @NotNull final FilePath filePath,
                                                 @NotNull final VcsFileRevision older,
                                                 @NotNull final VcsFileRevision newer) {
    new Task.Backgroundable(project, VcsBundle.message("file.history.diff.revisions.process")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          showDiff(project, filePath, older, newer, makeTitle(older), makeTitle(newer));
        }
        catch (final VcsException e) {
          LOG.info(e);
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(
            () -> Messages.showErrorDialog(VcsBundle.message("message.text.cannot.show.differences", e.getLocalizedMessage()),
                                           VcsBundle.message("message.title.show.differences")), null, project);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }

      @NotNull
      private @NlsContexts.Label String makeTitle(@NotNull VcsFileRevision revision) {
        return revision.getRevisionNumber().asString() +
               (revision instanceof CurrentRevision ? " (" + VcsBundle.message("diff.title.local") + ")" : "");
      }
    }.queue();
  }
}
