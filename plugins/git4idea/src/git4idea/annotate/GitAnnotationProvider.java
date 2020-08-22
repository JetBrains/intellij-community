// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.annotate;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetInterner;
import com.intellij.util.containers.Interner;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcs.CacheableAnnotationProvider;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.annotate.GitFileAnnotation.CommitInfo;
import git4idea.annotate.GitFileAnnotation.LineInfo;
import git4idea.commands.GitBinaryHandler;
import git4idea.commands.GitCommand;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVcsApplicationSettings.AnnotateDetectMovementsOption;
import git4idea.history.GitFileHistory;
import git4idea.history.GitHistoryProvider;
import git4idea.i18n.GitBundle;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public final class GitAnnotationProvider implements AnnotationProviderEx, CacheableAnnotationProvider {
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
  @NotNull
  public FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    return annotate(file, null);
  }

  @Override
  @NotNull
  public FileAnnotation annotate(@NotNull final VirtualFile file, @Nullable final VcsFileRevision revision) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException("Cannot annotate a directory");
    }

    if (revision == null) {
      Pair<FilePath, VcsRevisionNumber> pair = getPathAndRevision(myProject, file);
      return annotate(pair.first, pair.second, file);
    }
    else {
      FilePath filePath = ((VcsFileRevisionEx)revision).getPath();
      VcsRevisionNumber revisionNumber = revision.getRevisionNumber();
      return annotate(filePath, revisionNumber, file);
    }
  }

  @Override
  public String getActionName() {
    return ActionsBundle.message("action.Annotate.with.Blame.text");
  }

  @Override
  public boolean isAnnotationValid(@NotNull FilePath path, @NotNull VcsRevisionNumber revisionNumber) {
    return GitContentRevision.getRepositoryIfSubmodule(myProject, path) == null;
  }

  @NotNull
  @Override
  public FileAnnotation annotate(@NotNull final FilePath path, @NotNull final VcsRevisionNumber revision) throws VcsException {
    GitFileRevision fileRevision = new GitFileRevision(myProject, path, (GitRevisionNumber)revision);
    VcsVirtualFile file = new VcsVirtualFile(path.getPath(), fileRevision, VcsFileSystem.getInstance());

    return annotate(path, revision, file);
  }

  private static void setProgressIndicatorText(@Nullable String text) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) progress.setText(text);
  }

  @NotNull
  private GitFileAnnotation annotate(@NotNull FilePath filePath,
                                     @Nullable VcsRevisionNumber revision,
                                     @NotNull VirtualFile file) throws VcsException {
    VirtualFile root = GitUtil.getRootForFile(myProject, filePath);

    GitFileAnnotation fileAnnotation;
    if (revision != null) {
      Object annotatedData = myCache.getAnnotation(filePath, GitVcs.getKey(), revision);
      if (annotatedData instanceof CachedData) {
        fileAnnotation = restoreFromCache(file, revision, (CachedData)annotatedData);
      }
      else {
        fileAnnotation = doAnnotate(root, filePath, revision, file);
        myCache.putAnnotation(filePath, GitVcs.getKey(), revision, cacheData(fileAnnotation));
      }
    }
    else {
      fileAnnotation = doAnnotate(root, filePath, revision, file);
    }

    loadFileHistoryInBackground(fileAnnotation);
    loadCommitMessagesFromLog(root, fileAnnotation);

    return fileAnnotation;
  }

  @NotNull
  private GitFileAnnotation doAnnotate(@NotNull VirtualFile root,
                                       @NotNull FilePath filePath,
                                       @Nullable VcsRevisionNumber revision,
                                       @NotNull VirtualFile file) throws VcsException {
    setProgressIndicatorText(GitBundle.message("computing.annotation", file.getName()));

    GitBinaryHandler h = new GitBinaryHandler(myProject, root, GitCommand.BLAME);
    h.setStdoutSuppressed(true);
    h.addParameters("--porcelain", "-l", "-t");
    h.addParameters("--encoding=UTF-8");

    GitVcsApplicationSettings settings = GitVcsApplicationSettings.getInstance();
    if (settings.isIgnoreWhitespaces()) {
      h.addParameters("-w");
    }
    if (settings.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.INNER) {
      h.addParameters("-M");
    }
    else if (settings.getAnnotateDetectMovementsOption() == AnnotateDetectMovementsOption.OUTER) {
      h.addParameters("-C");
    }

    if (revision == null) {
      h.addParameters("HEAD");
    }
    else {
      h.addParameters(revision.asString());
    }
    h.endOptions();
    h.addRelativePaths(filePath);
    String output = new String(h.run(), StandardCharsets.UTF_8);

    return parseAnnotations(revision, file, root, output);
  }

  private void loadCommitMessagesFromLog(@NotNull VirtualFile root, @NotNull GitFileAnnotation annotation) {
    VcsLogManager logManager = VcsProjectLog.getInstance(myProject).getLogManager();
    if (logManager == null) return;

    VcsLogData dataManager = logManager.getDataManager();
    IndexDataGetter getter = dataManager.getIndex().getDataGetter();
    if (getter == null) return;

    Set<GitRevisionNumber> revisions = ContainerUtil.map2Set(annotation.getLines(), it -> it.getRevisionNumber());
    for (GitRevisionNumber revision: revisions) {
      if (annotation.getCommitMessage(revision) == null) {
        int commitIndex = dataManager.getCommitIndex(HashImpl.build(revision.asString()), root);
        String commitMessage = getter.getFullMessage(commitIndex);
        if (commitMessage != null) {
          annotation.setCommitMessage(revision, commitMessage);
        }
      }
    }
  }

  private void loadFileHistoryInBackground(@NotNull GitFileAnnotation fileAnnotation) {
    List<VcsFileRevision> fileRevisions = BackgroundTaskUtil.computeInBackgroundAndTryWait(
      () -> BackgroundTaskUtil.runUnderDisposeAwareIndicator(GitDisposable.getInstance(myProject), () -> {
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
      }),
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
    Interner<FilePath> pathInterner = new HashSetInterner<>();

    try {
      List<LineInfo> lines = new ArrayList<>();
      HashMap<String, CommitInfo> commits = new HashMap<>();
      for (StringScanner s = new StringScanner(output); s.hasMoreData(); ) {
        // parse header line
        String commitHash = s.spaceToken();
        if (commitHash.equals(GitRevisionNumber.NOT_COMMITTED_HASH)) {
          commitHash = null;
        }
        String s0 = s.spaceToken();
        int originalLineNum = Integer.parseInt(s0);
        String s1 = s.spaceToken();
        int lineNum = Integer.parseInt(s1);
        s.nextLine(); // skip number of lines in this group (if present)
        // parse commit information
        CommitInfo commit = commits.get(commitHash);
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
                previousFilePath = VcsUtil.getFilePath(root, value.substring(index + 1));
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

          commit = new CommitInfo(myProject, revisionNumber, filePath, committerDate, authorDate, author, subject,
                                previousRevisionNumber, previousFilePath);
          commits.put(commitHash, commit);
        }
        s.nextLine();

        int expectedLineNum = lines.size() + 1;
        if (lineNum != expectedLineNum) {
          throw new VcsException("Adding for info for line " + lineNum + " but we are expecting it to be for " + expectedLineNum);
        }

        LineInfo lineInfo = new LineInfo(commit, lineNum, originalLineNum);
        lines.add(lineInfo);
      }
      return new GitFileAnnotation(myProject, file, revision, lines);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error("Couldn't parse annotation: " + e.getMessage(), e, new Attachment("output.txt", output));
      throw new VcsException(e);
    }
  }

  @Override
  public void populateCache(@NotNull VirtualFile file) throws VcsException {
    Pair<FilePath, VcsRevisionNumber> pair = getPathAndRevision(myProject, file);
    FilePath filePath = pair.first;
    VcsRevisionNumber revision = pair.second;
    if (revision == null) return;

    Object annotatedData = myCache.getAnnotation(filePath, GitVcs.getKey(), revision);
    if (annotatedData instanceof CachedData) return;

    VirtualFile root = GitUtil.getRootForFile(myProject, filePath);
    GitFileAnnotation fileAnnotation = doAnnotate(root, filePath, revision, file);

    myCache.putAnnotation(filePath, GitVcs.getKey(), revision, cacheData(fileAnnotation));
  }

  @NotNull
  private GitFileAnnotation restoreFromCache(@NotNull VirtualFile file,
                                             @Nullable VcsRevisionNumber revisionNumber,
                                             @NotNull CachedData data) {
    return new GitFileAnnotation(myProject, file, revisionNumber, data.lines);
  }

  private static Pair<FilePath, VcsRevisionNumber> getPathAndRevision(@NotNull Project project, @NotNull VirtualFile file) {
    FilePath filePath = VcsUtil.getLastCommitPath(project, VcsUtil.getFilePath(file));
    VcsRevisionNumber revisionNumber = GitVcs.getInstance(project).getDiffProvider().getCurrentRevision(file);
    return Pair.create(filePath, revisionNumber);
  }

  @NotNull
  private static CachedData cacheData(@NotNull GitFileAnnotation annotation) {
    return new CachedData(annotation.getLines());
  }

  private static class CachedData {
    public final List<LineInfo> lines;

    CachedData(List<LineInfo> lines) {
      this.lines = lines;
    }
  }
}
