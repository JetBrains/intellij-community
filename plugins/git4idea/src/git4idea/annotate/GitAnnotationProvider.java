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
package git4idea.annotate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.annotate.GitFileAnnotation.LineInfo;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitFileHistory;
import git4idea.history.GitHistoryProvider;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class GitAnnotationProvider implements AnnotationProviderEx {
  private final Project myProject;
  @NonNls private static final String SUBJECT_KEY = "summary";
  @NonNls private static final String FILENAME_KEY = "filename";
  @NonNls private static final String PREVIOUS_KEY = "previous";
  @NonNls private static final String AUTHOR_KEY = "author";
  @NonNls private static final String AUTHOR_EMAIL_KEY = "author-mail";
  @NonNls private static final String COMMITTER_TIME_KEY = "committer-time";
  @NonNls private static final String AUTHOR_TIME_KEY = "author-time";
  private static final Logger LOG = Logger.getInstance(GitAnnotationProvider.class);

  @NotNull private final VcsHistoryCache myCache;
  @NotNull private final VcsUserRegistry myUserRegistry;

  public GitAnnotationProvider(@NotNull Project project) {
    myProject = project;
    myCache = ProjectLevelVcsManager.getInstance(myProject).getVcsHistoryCache();
    myUserRegistry = ServiceManager.getService(project, VcsUserRegistry.class);
  }

  @Override
  public boolean isCaching() {
    return true;
  }

  @NotNull
  public FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    return annotate(file, null);
  }

  @NotNull
  public FileAnnotation annotate(@NotNull final VirtualFile file, @Nullable final VcsFileRevision revision) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException("Cannot annotate a directory");
    }

    final FilePath currentFilePath = VcsUtil.getFilePath(file.getPath());
    final FilePath realFilePath;
    if (revision == null) {
      realFilePath = GitHistoryUtils.getLastCommitName(myProject, currentFilePath);
    }
    else {
      realFilePath = ((VcsFileRevisionEx)revision).getPath();
    }
    VcsRevisionNumber revisionNumber = revision != null ? revision.getRevisionNumber() : null;

    return annotate(realFilePath, revisionNumber, file);
  }

  @NotNull
  @Override
  public FileAnnotation annotate(@NotNull final FilePath path, @NotNull final VcsRevisionNumber revision) throws VcsException {
    GitFileRevision fileRevision = new GitFileRevision(myProject, path, (GitRevisionNumber)revision);
    VcsVirtualFile file = new VcsVirtualFile(path.getPath(), fileRevision, VcsFileSystem.getInstance());

    return annotate(path, revision, file);
  }

  private static void setProgressIndicatorText(@Nullable String text) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) progress.setText(text);
  }

  @NotNull
  private GitFileAnnotation annotate(@NotNull final FilePath repositoryFilePath,
                                     @Nullable final VcsRevisionNumber revision,
                                     @NotNull final VirtualFile file) throws VcsException {
    GitVcs vcs = GitVcs.getInstance(myProject);

    VcsRevisionNumber actualRevision = revision != null ? revision : vcs.getDiffProvider().getCurrentRevision(file);

    if (actualRevision != null) {
      Object annotatedData = myCache.get(repositoryFilePath, GitVcs.getKey(), actualRevision);
      if (annotatedData instanceof CachedData) return restoreFromCache(file, actualRevision, (CachedData)annotatedData);
    }

    GitFileAnnotation fileAnnotation = doAnnotate(repositoryFilePath, actualRevision, file);

    if (actualRevision != null) {
      myCache.put(repositoryFilePath, GitVcs.getKey(), actualRevision, cacheData(fileAnnotation));
    }

    return fileAnnotation;
  }

  @NotNull
  private GitFileAnnotation doAnnotate(@NotNull final FilePath repositoryFilePath,
                                       @Nullable final VcsRevisionNumber revision,
                                       @NotNull final VirtualFile file) throws VcsException {
    setProgressIndicatorText(GitBundle.message("computing.annotation", file.getName()));

    VirtualFile root = GitUtil.getGitRoot(repositoryFilePath);
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.BLAME);
    h.setStdoutSuppressed(true);
    h.addParameters("--porcelain", "-l", "-t", "-w");
    h.addParameters("--encoding=UTF-8");
    if (revision == null) {
      h.addParameters("HEAD");
    }
    else {
      h.addParameters(revision.asString());
    }
    h.endOptions();
    h.addRelativePaths(repositoryFilePath);
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();

    GitFileAnnotation fileAnnotation = parseAnnotations(revision, file, root, output);

    loadFileHistoryInBackground(fileAnnotation);

    return fileAnnotation;
  }

  private void loadFileHistoryInBackground(@NotNull GitFileAnnotation fileAnnotation) {
    List<VcsFileRevision> fileRevisions = BackgroundTaskUtil.computeInBackgroundAndTryWait(
      () -> {
        return BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
          try {
            VirtualFile file = fileAnnotation.getFile();
            FilePath filePath = VcsUtil.getFilePath(file);
            VcsRevisionNumber currentRevision = fileAnnotation.getCurrentRevision();

            if (file.isInLocalFileSystem() || currentRevision == null) {
              return loadFileHistory(filePath);
            }
            else {
              return GitFileHistory.collectHistoryForRevision(myProject, filePath, currentRevision);
            }
          }
          catch (VcsException e) {
            LOG.error(e);
            return null;
          }
        });
      },
      (revisions) -> {
        if (revisions == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
          GitFileAnnotation newFileAnnotation = new GitFileAnnotation(fileAnnotation);
          newFileAnnotation.setRevisions(revisions);
          fileAnnotation.reload(newFileAnnotation);
        }, myProject.getDisposed());
      },
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    );

    if (fileRevisions != null) {
      fileAnnotation.setRevisions(fileRevisions);
    }
  }

  @Nullable
  private List<VcsFileRevision> loadFileHistory(@NotNull FilePath filePath) throws VcsException {
    GitVcs vcs = GitVcs.getInstance(myProject);
    GitHistoryProvider historyProvider = vcs.getVcsHistoryProvider();

    VcsAbstractHistorySession cachedSession = myCache.getFull(filePath, vcs.getKeyInstanceMethod(), historyProvider);
    if (cachedSession != null && !ContainerUtil.isEmpty(cachedSession.getRevisionList())) {
      return cachedSession.getRevisionList();
    }
    else {
      VcsAbstractHistorySession session = historyProvider.createSessionFor(filePath);
      if (session == null) return null;

      myCache.put(filePath, null, vcs.getKeyInstanceMethod(), session, historyProvider, true);

      return session.getRevisionList();
    }
  }

  @NotNull
  private GitFileAnnotation parseAnnotations(@Nullable VcsRevisionNumber revision,
                                             @NotNull VirtualFile file,
                                             @NotNull VirtualFile root,
                                             @NotNull String output) throws VcsException {
    Interner<FilePath> pathInterner = new Interner<>();

    try {
      List<LineInfo> lines = new ArrayList<>();
      HashMap<String, LineInfo> commits = new HashMap<>();
      for (StringScanner s = new StringScanner(output); s.hasMoreData(); ) {
        // parse header line
        String commitHash = s.spaceToken();
        if (commitHash.equals(GitRevisionNumber.NOT_COMMITTED_HASH)) {
          commitHash = null;
        }
        s.spaceToken(); // skip revision line number
        String s1 = s.spaceToken();
        int lineNum = Integer.parseInt(s1);
        s.nextLine();
        // parse commit information
        LineInfo commit = commits.get(commitHash);
        if (commit != null || commitHash == null) {
          while (s.hasMoreData() && !s.startsWith('\t')) {
            s.nextLine();
          }
        }
        else {
          Date committerDate = null;
          FilePath filePath = null;
          String subject = null;
          String authorName = null;
          String authorEmail = null;
          Date authorDate = null;
          String previousRevision = null;
          FilePath previousFilePath = null;

          while (s.hasMoreData() && !s.startsWith('\t')) {
            String key = s.spaceToken();
            String value = s.line();
            if (SUBJECT_KEY.equals(key)) {
              subject = value;
            }
            else if (AUTHOR_KEY.equals(key)) {
              authorName = value;
            }
            else if (AUTHOR_TIME_KEY.equals(key)) {
              authorDate = GitUtil.parseTimestamp(value);
            }
            else if (COMMITTER_TIME_KEY.equals(key)) {
              committerDate = GitUtil.parseTimestamp(value);
            }
            else if (FILENAME_KEY.equals(key)) {
              filePath = VcsUtil.getFilePath(root, value);
            }
            else if (AUTHOR_EMAIL_KEY.equals(key)) {
              authorEmail = value;
              if (authorEmail.startsWith("<") && authorEmail.endsWith(">")) {
                authorEmail = authorEmail.substring(1, authorEmail.length() - 1);
              }
            }
            else if (PREVIOUS_KEY.equals(key)) {
              int index = value.indexOf(' ');
              if (index != -1) {
                previousRevision = value.substring(0, index);
                previousFilePath = VcsUtil.getFilePath(root, value.substring(index + 1, value.length()));
              }
            }
          }

          if (authorDate == null || committerDate == null || filePath == null || authorName == null || authorEmail == null || subject == null) {
            throw new VcsException("Output for line " + lineNum + " lacks necessary data");
          }

          GitRevisionNumber revisionNumber = new GitRevisionNumber(commitHash);
          VcsUser author = myUserRegistry.createUser(authorName, authorEmail);
          GitRevisionNumber previousRevisionNumber = previousRevision != null ? new GitRevisionNumber(previousRevision) : null;


          filePath = pathInterner.intern(filePath);
          if (previousFilePath != null) previousFilePath = pathInterner.intern(previousFilePath);

          commit = new LineInfo(myProject, revisionNumber, filePath, committerDate, authorDate, author, subject,
                                previousRevisionNumber, previousFilePath);
          commits.put(commitHash, commit);
        }
        s.nextLine();

        int expectedLineNum = lines.size() + 1;
        if (lineNum != expectedLineNum) {
          throw new VcsException("Adding for info for line " + lineNum + " but we are expecting it to be for " + expectedLineNum);
        }

        lines.add(commit);
      }
      return new GitFileAnnotation(myProject, file, revision, lines);
    }
    catch (Exception e) {
      LOG.error("Couldn't parse annotation: " + e, new Attachment("output.txt", output));
      throw new VcsException(e);
    }
  }

  @NotNull
  private GitFileAnnotation restoreFromCache(@NotNull VirtualFile file,
                                             @Nullable VcsRevisionNumber revisionNumber,
                                             @NotNull CachedData data) {
    GitFileAnnotation fileAnnotation = new GitFileAnnotation(myProject, file, revisionNumber, data.lines);

    loadFileHistoryInBackground(fileAnnotation);

    return fileAnnotation;
  }

  @NotNull
  private CachedData cacheData(@NotNull GitFileAnnotation annotation) {
    return new CachedData(annotation.getLines());
  }

  private static class CachedData {
    public final List<LineInfo> lines;

    public CachedData(List<LineInfo> lines) {
      this.lines = lines;
    }
  }
}
