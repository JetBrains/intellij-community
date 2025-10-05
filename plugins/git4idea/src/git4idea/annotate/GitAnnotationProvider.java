// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.annotate;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.AnnotationWarning;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static git4idea.annotate.GitAnnotationProviderKt.getAnnotationFromCache;

@Service(Service.Level.PROJECT)
public final class GitAnnotationProvider implements AnnotationProviderEx, CacheableAnnotationProvider {
  private static final @NonNls String SUBJECT_KEY = "summary";
  private static final @NonNls String FILENAME_KEY = "filename";
  private static final @NonNls String PREVIOUS_KEY = "previous";
  private static final @NonNls String AUTHOR_KEY = "author";
  private static final @NonNls String AUTHOR_EMAIL_KEY = "author-mail";
  private static final @NonNls String COMMITTER_TIME_KEY = "committer-time";
  private static final @NonNls String AUTHOR_TIME_KEY = "author-time";
  private static final Logger LOG = Logger.getInstance(GitAnnotationProvider.class);
  private static final Logger TIME_LOG = Logger.getInstance("#time." + GitAnnotationProvider.class.getName());

  private final Project myProject;
  private final @NotNull VcsHistoryCache myCache;

  public GitAnnotationProvider(@NotNull Project project) {
    myProject = project;
    myCache = ProjectLevelVcsManager.getInstance(myProject).getVcsHistoryCache();
  }

  @Override
  public @NotNull FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    return annotate(file, null);
  }

  @Override
  public @NotNull FileAnnotation annotate(final @NotNull VirtualFile file, final @Nullable VcsFileRevision revision) throws VcsException {
    return logTime(() -> {
      if (file.isDirectory()) {
        throw new VcsException(GitBundle.message("annotate.cannot.annotate.dir"));
      }

      if (revision == null) {
        FilePath filePath = VcsUtil.getLastCommitPath(myProject, VcsUtil.getFilePath(file));
        VcsRevisionNumber currentRevision = getCurrentRevision(file);
        // Only get cached last revision here to avoid slowdowns, we'll compute it on annotation caching
        VcsRevisionNumber cachedLastRevision =
          currentRevision != null ? myCache.getLastRevision(filePath, GitVcs.getKey(), currentRevision) : null;
        return annotate(filePath, cachedLastRevision, file);
      }
      else {
        FilePath filePath = ((VcsFileRevisionEx)revision).getPath();
        VcsRevisionNumber revisionNumber = revision.getRevisionNumber();
        return annotate(filePath, revisionNumber, file);
      }
    });
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) @Nullable String getCustomActionName() {
    return ActionsBundle.message("action.Annotate.with.Blame.text");
  }

  @Override
  public boolean isAnnotationValid(@NotNull FilePath path, @NotNull VcsRevisionNumber revisionNumber) {
    return revisionNumber instanceof GitRevisionNumber &&
           GitContentRevision.getRepositoryIfSubmodule(myProject, path) == null;
  }

  @Override
  public @NotNull FileAnnotation annotate(final @NotNull FilePath path, final @NotNull VcsRevisionNumber revision) throws VcsException {
    return logTime(() -> {
      GitFileRevision fileRevision = new GitFileRevision(myProject, path, (GitRevisionNumber)revision);
      VcsVirtualFile file = new VcsVirtualFile(path, fileRevision);

      return annotate(path, revision, file);
    });
  }

  @Override
  public @Nullable AnnotationWarning getAnnotationWarnings(@NotNull FileAnnotation fileAnnotation) {
    GitFileAnnotation gitFileAnnotation = ObjectUtils.tryCast(fileAnnotation, GitFileAnnotation.class);
    return gitFileAnnotation == null ? null : GitAnnotationWarnings.getInstance(myProject).getAnnotationWarnings(gitFileAnnotation);
  }

  private @NotNull GitFileAnnotation annotate(@NotNull FilePath filePath,
                                              @Nullable VcsRevisionNumber revision,
                                              @NotNull VirtualFile file) throws VcsException {
    VirtualFile root = GitUtil.getRootForFile(myProject, filePath);

    GitFileAnnotation fileAnnotation = null;
    if (revision != null) {
      fileAnnotation = getCached(filePath, revision, file);
    }

    if (fileAnnotation == null) {
      fileAnnotation = doAnnotate(root, filePath, revision, file);

      if (revision != null) {
        cache(filePath, revision, fileAnnotation);
      }
      else { // compute last revision and cache annotations in the background
        GitFileAnnotation finalFileAnnotation = fileAnnotation;
        BackgroundTaskUtil.executeOnPooledThread(GitDisposable.getInstance(myProject), () -> {
          VcsRevisionNumber lastRevision = getLastRevision(filePath, getCurrentRevision(file));
          if (lastRevision != null) {
            cache(filePath, lastRevision, finalFileAnnotation);
          }
        });
      }
    }

    if (fileAnnotation.getRevisions() == null) {
      loadFileHistoryInBackground(fileAnnotation);
      loadCommitMessagesFromLog(root, fileAnnotation);
    }

    return fileAnnotation;
  }

  @ApiStatus.Experimental
  public @Nullable GitFileAnnotation getCached(@NotNull FilePath filePath,
                                               @Nullable VcsRevisionNumber revision,
                                               @NotNull VirtualFile file) {
    Object annotatedData = myCache.getAnnotation(filePath, GitVcs.getKey(), revision);
    if (annotatedData instanceof CachedData) {
      return restoreFromCache(file, filePath, revision, (CachedData)annotatedData);
    }
    return null;
  }

  @ApiStatus.Experimental
  public void cache(@NotNull FilePath filePath, @NotNull VcsRevisionNumber revision, GitFileAnnotation fileAnnotation) {
    myCache.putAnnotation(filePath, GitVcs.getKey(), revision, cacheData(fileAnnotation));
  }

  @ApiStatus.Experimental
  public interface GitRawAnnotationProvider {

    ProjectExtensionPointName<GitRawAnnotationProvider> EP_NAME = new ProjectExtensionPointName<>("Git4Idea.gitRawAnnotationProvider");

    @NotNull
    @NonNls
    String getId();

    @Nullable
    GitFileAnnotation annotate(@NotNull Project project,
                               @NotNull VirtualFile root,
                               @NotNull FilePath filePath,
                               @Nullable VcsRevisionNumber revision,
                               @NotNull VirtualFile file) throws VcsException;

    default GitFileAnnotation annotate(final @NotNull VirtualFile file, final @Nullable VcsFileRevision revision) {
      return null;
    }

    static boolean isDefault(@NotNull String providerId) {
      return providerId.equals(DefaultGitAnnotationProvider.ID);
    }
  }

  private @NotNull GitFileAnnotation doAnnotate(@NotNull VirtualFile root,
                                                @NotNull FilePath filePath,
                                                @Nullable VcsRevisionNumber revision,
                                                @NotNull VirtualFile file) throws VcsException {
    setProgressIndicatorText(GitBundle.message("computing.annotation", file.getName()));

    return myProject.getService(GitAnnotationService.class).annotate(root, filePath, revision, file);
  }

  private static class DefaultGitAnnotationProvider implements GitRawAnnotationProvider {

    private static final String ID = "default";

    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull GitFileAnnotation annotate(@NotNull Project project,
                                               @NotNull VirtualFile root,
                                               @NotNull FilePath filePath,
                                               @Nullable VcsRevisionNumber revision,
                                               @NotNull VirtualFile file) throws VcsException {
      // parseAnnotations rely on the fact that revision should be the last file's modified revision
      if (revision == null) {
        GitAnnotationProvider provider = project.getService(GitAnnotationProvider.class);
        revision = provider.getLastRevision(filePath, provider.getCurrentRevision(file));
      }

      // binary handler to preserve CR symbols intact
      GitBinaryHandler h = new GitBinaryHandler(project, root, GitCommand.BLAME);
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

      if (LOG.isDebugEnabled()) {
        LOG.debug("Starting " + h.printableCommandLine());
      }

      String output = new String(h.run(), StandardCharsets.UTF_8);

      return parseAnnotations(project, revision, file, filePath, root, output);
    }
  }

  /**
   * Read missing tooltip information without waiting for optional async callback in {@link #loadFileHistoryInBackground}.
   * <p>
   * This can't fully replace slow git request, as we do not read {@link GitFileAnnotation#setRevisions(List)} from {@link VcsLogData}.
   */
  private void loadCommitMessagesFromLog(@NotNull VirtualFile root, @NotNull GitFileAnnotation annotation) {
    VcsLogManager logManager = VcsProjectLog.getInstance(myProject).getLogManager();
    if (logManager == null) return;

    VcsLogData dataManager = logManager.getDataManager();
    IndexDataGetter getter = dataManager.getIndex().getDataGetter();
    if (getter == null) return;

    Set<GitRevisionNumber> revisions = ContainerUtil.map2Set(annotation.getLines(), it -> it.getRevisionNumber());
    for (GitRevisionNumber revision : revisions) {
      // non-null if info was already loaded by fast synchronous path in 'loadFileHistoryInBackground'
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
      () -> {
        VirtualFile file = fileAnnotation.getFile();
        FilePath filePath = VcsUtil.getFilePath(file);
        VcsRevisionNumber currentRevision = fileAnnotation.getCurrentRevision();

        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(file);
        if (repository == null) return null;

        return BackgroundTaskUtil.runUnderDisposeAwareIndicator(repository, () -> {
          try {
            if (file.isInLocalFileSystem() || currentRevision == null) {
              return loadFileHistory(filePath);
            }
            else {
              return GitFileHistory.collectHistoryForRevision(myProject, filePath, currentRevision);
            }
          }
          catch (VcsException e) {
            LOG.warn(e);
            return null;
          }
        });
      },
      (revisions) -> {
        if (revisions == null) return;

        GitFileAnnotation newFileAnnotation =
          new GitFileAnnotation(fileAnnotation.getProject(),
                                fileAnnotation.getFile(),
                                fileAnnotation.getFilePath(),
                                fileAnnotation.getCurrentRevision(),
                                fileAnnotation.getLines());
        newFileAnnotation.setRevisions(revisions);

        ApplicationManager.getApplication().invokeLater(() -> {
          fileAnnotation.reload(newFileAnnotation);
        }, myProject.getDisposed());
      },
      ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS
    );

    if (fileRevisions != null) {
      fileAnnotation.setRevisions(fileRevisions);
    }
  }

  private @NotNull List<VcsFileRevision> loadFileHistory(@NotNull FilePath filePath) throws VcsException {
    GitVcs vcs = GitVcs.getInstance(myProject);
    GitHistoryProvider historyProvider = vcs.getVcsHistoryProvider();

    VcsAbstractHistorySession cachedSession = myCache.getSession(filePath, vcs.getKeyInstanceMethod(), historyProvider, false);
    if (cachedSession != null && !ContainerUtil.isEmpty(cachedSession.getRevisionList())) {
      return cachedSession.getRevisionList();
    }
    else {
      VcsAbstractHistorySession session = historyProvider.createSessionFor(filePath);

      myCache.putSession(filePath, null, vcs.getKeyInstanceMethod(), session, historyProvider, true);

      return session.getRevisionList();
    }
  }

  private static @NotNull GitFileAnnotation parseAnnotations(@NotNull Project project,
                                                             @Nullable VcsRevisionNumber revision,
                                                             @NotNull VirtualFile file,
                                                             @NotNull FilePath filePath,
                                                             @NotNull VirtualFile root,
                                                             @NotNull String output) throws VcsException {
    Interner<FilePath> pathInterner = Interner.createInterner();

    if (StringUtil.isEmpty(output)) {
      LOG.warn("Git annotations are empty for file " + file.getPath() + " in revision " + revision);
    }

    VcsUserRegistry userRegistry = project.getService(VcsUserRegistry.class);

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
          FilePath commitFilePath = null;
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
              commitFilePath = VcsUtil.getFilePath(root, value);
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

          if (authorDate == null ||
              committerDate == null ||
              commitFilePath == null ||
              authorName == null ||
              authorEmail == null ||
              subject == null) {
            throw new VcsException(GitBundle.message("annotate.output.lack.data", lineNum));
          }

          GitRevisionNumber revisionNumber = new GitRevisionNumber(commitHash);
          VcsUser author = userRegistry.createUser(authorName, authorEmail);
          GitRevisionNumber previousRevisionNumber = previousRevision != null ? new GitRevisionNumber(previousRevision) : null;


          commitFilePath = pathInterner.intern(commitFilePath);
          if (previousFilePath != null) previousFilePath = pathInterner.intern(previousFilePath);

          commit = new CommitInfo(project, revisionNumber, commitFilePath, committerDate, authorDate, author, subject,
                                  previousRevisionNumber, previousFilePath);
          commits.put(commitHash, commit);
        }
        s.nextLine();

        int expectedLineNum = lines.size() + 1;
        if (lineNum != expectedLineNum) {
          throw new VcsException(GitBundle.message("annotate.line.mismatch.exception", lineNum, expectedLineNum));
        }

        //noinspection ConstantConditions
        LineInfo lineInfo = new LineInfo(commit, lineNum, originalLineNum);
        lines.add(lineInfo);
      }
      return new GitFileAnnotation(project, file, filePath, revision, lines);
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
    Pair<FilePath, VcsRevisionNumber> pair = getPathAndRevision(file);
    FilePath filePath = pair.first;
    VcsRevisionNumber revision = pair.second;
    if (revision == null) return;

    Object annotatedData = myCache.getAnnotation(filePath, GitVcs.getKey(), revision);
    if (annotatedData instanceof CachedData) return;

    VirtualFile root = GitUtil.getRootForFile(myProject, filePath);
    GitFileAnnotation fileAnnotation = logTime(() -> doAnnotate(root, filePath, revision, file));

    cache(filePath, revision, fileAnnotation);
  }

  @Override
  public @Nullable FileAnnotation getFromCache(@NotNull VirtualFile file) {
    return getAnnotationFromCache(myProject, file);
  }

  private @NotNull GitFileAnnotation restoreFromCache(@NotNull VirtualFile file,
                                                      @NotNull FilePath filePath,
                                                      @Nullable VcsRevisionNumber revisionNumber,
                                                      @NotNull CachedData data) {
    return new GitFileAnnotation(myProject, file, filePath, revisionNumber, data.lines);
  }

  private @NotNull Pair<FilePath, VcsRevisionNumber> getPathAndRevision(@NotNull VirtualFile file) {
    FilePath filePath = VcsUtil.getLastCommitPath(myProject, VcsUtil.getFilePath(file));
    VcsRevisionNumber currentRevision = getCurrentRevision(file);
    VcsRevisionNumber lastRevision = getLastRevision(filePath, currentRevision);
    return Pair.create(filePath, lastRevision);
  }

  /**
   * @param currentRevision HEAD revision number
   * @return last revision filePath was modified in
   */
  private @Nullable VcsRevisionNumber getLastRevision(@NotNull FilePath filePath, @Nullable VcsRevisionNumber currentRevision) {
    VcsKey gitKey = GitVcs.getKey();
    if (currentRevision != null) {
      VcsRevisionNumber cachedLastRevision = myCache.getLastRevision(filePath, gitKey, currentRevision);
      if (cachedLastRevision != null) {
        return cachedLastRevision;
      }
    }

    try {
      VcsRevisionNumber lastRevision = GitHistoryUtils.getCurrentRevision(myProject, filePath, GitUtil.HEAD);
      if (currentRevision != null && lastRevision != null) {
        myCache.putLastRevision(filePath, gitKey, currentRevision, lastRevision);
      }
      return lastRevision;
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  /**
   * @return HEAD revision number
   */
  private @Nullable VcsRevisionNumber getCurrentRevision(@NotNull VirtualFile file) {
    GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(file);
    if (repository == null) return null;

    String currentRevision = repository.getCurrentRevision();
    if (currentRevision == null) return null;

    return new GitRevisionNumber(currentRevision);
  }

  private static void setProgressIndicatorText(@NlsContexts.ProgressText @Nullable String text) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) progress.setText(text);
  }

  private static @NotNull CachedData cacheData(@NotNull GitFileAnnotation annotation) {
    return new CachedData(annotation.getLines());
  }

  static class CachedData {
    public final List<LineInfo> lines;

    CachedData(List<LineInfo> lines) {
      this.lines = lines;
    }
  }

  private static <T> T logTime(ThrowableComputable<T, VcsException> computable) throws VcsException {
    long start = -1;
    try {
      if (TIME_LOG.isDebugEnabled()) {
        start = System.currentTimeMillis();
      }
      return computable.compute();
    }
    finally {
      if (start > -1) {
        TIME_LOG.debug("Git annotations took " + (System.currentTimeMillis() - start) + "ms");
      }
    }
  }
}
