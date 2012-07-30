/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public class VcsHistoryUtil {

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
   * @param project   project under vcs control.
   * @param filePath  file which revisions are compared.
   * @param revision1 first revision - 'before', to the left.
   * @param revision2 second revision - 'after', to the right.
   * @throws com.intellij.openapi.vcs.VcsException
   * @throws java.io.IOException
   */
  public static void showDiff(@NotNull final Project project, @NotNull FilePath filePath,
                              @NotNull VcsFileRevision revision1, @NotNull VcsFileRevision revision2,
                              @NotNull String title1, @NotNull String title2) throws VcsException, IOException {
    final byte[] content1 = loadRevisionContent(revision1);
    final byte[] content2 = loadRevisionContent(revision2);

    final SimpleDiffRequest diffData = new SimpleDiffRequest(project, filePath.getPresentableUrl());
    diffData.addHint(DiffTool.HINT_SHOW_FRAME);
    final Document doc = filePath.getDocument();
    final Charset charset = filePath.getCharset();
    final FileType fileType = filePath.getFileType();
    diffData.setContentTitles(title1, title2);
    final Ref<VirtualFile> f1 = new Ref<VirtualFile>(null);
    final Ref<VirtualFile> f2 = new Ref<VirtualFile>(null);

    if (fileType.isBinary()) {
      final File file1 = FileUtil.createTempFile(revision1.getRevisionNumber().asString(), filePath.getName());
      final File file2 = FileUtil.createTempFile(revision2.getRevisionNumber().asString(), filePath.getName());
      try {
        final FileOutputStream fos1 = new FileOutputStream(file1);
        fos1.write(content1);
        final FileOutputStream fos2 = new FileOutputStream(file2);
        fos2.write(content2);
        fos1.close();
        fos2.close();
        f1.set(LocalFileSystem.getInstance().findFileByIoFile(file1));
        f2.set(LocalFileSystem.getInstance().findFileByIoFile(file2));
      } catch(Exception e) {//
      }
    }
    if (f1.isNull() || f2.isNull()) {
      diffData.setContents(createContent(project, content1, revision1, doc, charset, fileType, filePath.getPath()),
                           createContent(project, content2, revision2, doc, charset, fileType, filePath.getPath()));
    } else {
      diffData.setContents(new FileContent(project, f1.get()), new FileContent(project, f2.get()));
    }
    WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
      public void run() {
        DiffManager.getInstance().getDiffTool().show(diffData);
        if (!f1.isNull() || !f2.isNull()) {
          Disposer.register(project, new Disposable() {
            @Override
            public void dispose() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  try {
                    if (!f1.isNull()) {
                      f1.get().delete(this);
                    }
                    if (!f2.isNull()) {
                      f2.get().delete(this);
                    }
                  }
                  catch (IOException e) {//
                  }
                }
              });
            }
          });
        }
      }
    }, null, project);
  }

    public static byte[] loadRevisionContent(@NotNull VcsFileRevision revision) throws VcsException, IOException {
    byte[] content = revision.getContent();
    if (content == null) {
      revision.loadContent();
      content = revision.getContent();
    }
    if (content == null) throw new VcsException("Failed to load content for revision " + revision.getRevisionNumber().asString());
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

  public static String loadRevisionContentGuessEncoding(final VcsFileRevision revision, @Nullable final Project project) throws VcsException, IOException {
    final byte[] bytes = loadRevisionContent(revision);

    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : null;
    if (e == null) {
      e = EncodingManager.getInstance();
    }

    return CharsetToolkit.bytesToString(bytes, e.getDefaultCharset());
  }

  private static DiffContent createContent(Project project, byte[] content1, VcsFileRevision revision, Document doc, Charset charset, FileType fileType, String filePath) {
    if (isCurrent(revision) && (doc != null)) { return new DocumentContent(project, doc); }
    return new BinaryContent(content1, charset, fileType, filePath);
  }

  private static boolean isCurrent(VcsFileRevision revision) {
    return revision instanceof CurrentRevision;
  }


  /**
   * Shows difference between two revisions of a file in a diff tool.
   * The content of revisions is queried in a background thread.
   * If {@code findOlderNewer} is set to {@code true}, revisions may be specified in any order:
   * this method will sort them so that the older revision is at the left, and the newer one is at the right.
   * @param findOlderNewer specify {@code true} to let method compare revisions, and put the older revision at the left, and newer revision
   *                       at the right.<br/>
   *                       Specify {@code false} to put {@code revision1} at the left, and {@code revision2} at the right.
   * @see #showDiff(Project, FilePath, VcsFileRevision, VcsFileRevision, String, String)
   */
  public static void showDifferencesInBackground(@NotNull final Project project, @NotNull final FilePath filePath,
                                                 @NotNull final VcsFileRevision revision1, @NotNull final VcsFileRevision revision2,
                                                 final boolean findOlderNewer) {
    new Task.Backgroundable(project, "Loading revisions to compare") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        VcsFileRevision left = revision1;
        VcsFileRevision right = revision2;
        if (findOlderNewer) {
          Pair<VcsFileRevision, VcsFileRevision> pair = sortRevisions(revision1, revision2);
          left = pair.first;
          right = pair.second;
        }

        try {
          final String leftTitle = left.getRevisionNumber().asString() +
                                   (left instanceof CurrentRevision ? " (" + VcsBundle.message("diff.title.local") + ")" : "");
          final String rightTitle = right.getRevisionNumber().asString() +
                                    (right instanceof CurrentRevision ? " (" + VcsBundle.message("diff.title.local") + ")" : "");
          showDiff(project, filePath, left, right, leftTitle, rightTitle);
        }
        catch (final VcsException e) {
          LOG.info(e);
          WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
            public void run() {
              Messages.showErrorDialog(VcsBundle.message("message.text.cannot.show.differences", e.getLocalizedMessage()),
                                       VcsBundle.message("message.title.show.differences"));
            }
          }, null, project);
        }
        catch (IOException e) {
          LOG.info(e);
        }
        catch (ProcessCanceledException ex) {
          LOG.info(ex);
        }
      }
    }.queue();
  }

  /**
   * Compares the given revisions and returns a pair of them, where the first one is older, and second is newer.
   */
  @NotNull
  public static Pair<VcsFileRevision, VcsFileRevision> sortRevisions(@NotNull VcsFileRevision revision1,
                                                                     @NotNull VcsFileRevision revision2) {
    VcsFileRevision left = revision1;
    VcsFileRevision right = revision2;
    if (compare(revision1, revision2) > 0) {
      left = revision2;
      right = revision1;
    }
    return Pair.create(left, right);
  }

}
