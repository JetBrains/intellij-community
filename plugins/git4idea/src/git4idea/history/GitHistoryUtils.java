// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionDescriptionImpl;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitCommit;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.history.browser.SHAHash;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * A collection of methods for retrieving history information from native Git.
 */
public final class GitHistoryUtils {
  private static final Logger LOG = Logger.getInstance(GitHistoryUtils.class);

  private GitHistoryUtils() {
  }

  /**
   * Load commit information in a form of {@link GitCommit} (containing commit details and changes to commit parents)
   * in the repository using `git log` command.
   *
   * @param project        context project
   * @param root           git repository root
   * @param commitConsumer consumer for commits
   * @param parameters     additional parameters for `git log` command
   * @throws VcsException if there is a problem with running git
   */
  @SuppressWarnings("unused") // used externally
  public static void loadDetails(@NotNull Project project,
                                 @NotNull VirtualFile root,
                                 @NotNull Consumer<? super GitCommit> commitConsumer,
                                 String @NotNull ... parameters) throws VcsException {
    GitLogUtil.readFullDetails(project, root, commitConsumer, parameters);
  }

  /**
   * Load commit information in a form of {@link TimedVcsCommit} (containing hash, parents and commit time)
   * in the repository using `git log` command.
   *
   * @param project        context project
   * @param root           git repository root
   * @param commitConsumer consumer for commits
   * @param parameters     additional parameters for `git log` command
   * @throws VcsException if there is a problem with running git
   */
  public static void loadTimedCommits(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull Consumer<? super TimedVcsCommit> commitConsumer,
                                      String @NotNull ... parameters) throws VcsException {
    GitLogUtil.readTimedCommits(project, root, Arrays.asList(parameters), null, null, commitConsumer);
  }

  /**
   * Collect commit information in a form of {@link TimedVcsCommit} (containing hash, parents and commit time)
   * in the repository using `git log` command.
   *
   * @param project    context project
   * @param root       git repository root
   * @param parameters additional parameters for `git log` command
   * @throws VcsException if there is a problem with running git
   * @see GitHistoryUtils#formHashParameters(Project, Collection)
   */
  public static @NotNull List<? extends TimedVcsCommit> collectTimedCommits(@NotNull Project project,
                                                                            @NotNull VirtualFile root,
                                                                            String @NotNull ... parameters) throws VcsException {
    List<TimedVcsCommit> commits = new ArrayList<>();
    loadTimedCommits(project, root, commits::add, parameters);
    return commits;
  }

  /**
   * Collect commit information in a form of {@link VcsCommitMetadata} (containing commit details, but no changes)
   * for the specified hashes or references.
   * <br/>
   * Note: this method accepts a list of <em>hashes or references</em>, not arbitrary parameters.
   *
   * @param project context project
   * @param root    git repository root
   * @param hashes  hashes or references
   * @return a list of {@link VcsCommitMetadata} (one for each specified hash or reference) or null if something went wrong
   * @throws VcsException if there is a problem with running git
   */
  @Nullable
  public static List<? extends VcsCommitMetadata> collectCommitsMetadata(@NotNull Project project,
                                                                         @NotNull VirtualFile root,
                                                                         String @NotNull ... hashes)
    throws VcsException {
    List<? extends VcsCommitMetadata> result = GitLogUtil.collectMetadata(project, root, Arrays.asList(hashes));
    if (result.size() != hashes.length) return null;
    return result;
  }

  /**
   * Collect commit information in a form of {@link GitCommit} (containing commit details and changes to commit parents)
   * in the repository using `git log` command.
   * If changes for commits are not needed, it is recommended to use other methods,
   * such as {@link GitHistoryUtils#collectCommitsMetadata(Project, VirtualFile, String...)}
   * or {@link GitHistoryUtils#collectTimedCommits(Project, VirtualFile, String...)}.
   * <br/>
   * Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   * and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.
   *
   * @param project    context project
   * @param root       git repository root
   * @param parameters additional parameters for `git log` command
   * @return a list of {@link GitCommit}
   * @throws VcsException if there is a problem with running git
   * @see GitHistoryUtils#formHashParameters(Project, Collection)
   */
  @NotNull
  public static List<GitCommit> history(@NotNull Project project, @NotNull VirtualFile root, String... parameters)
    throws VcsException {
    VcsLogObjectsFactory factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    List<GitCommit> commits = new ArrayList<>();
    try {
      GitLogUtil.readFullDetails(project, root, commits::add, parameters);
    }
    catch (VcsException e) {
      if (commits.isEmpty()) {
        throw e;
      }
      LOG.warn("Error during loading details, returning partially loaded commits\n", e);
    }
    return commits;
  }

  /**
   * Create a proper list of parameters for `git log` command from a list of hashes.
   *
   * @param project a {@link Project} instance for which the command is going to be invoked. Used to get Git version.
   * @param hashes  a list of hashes to call `git log` for
   * @return a array of parameters that could be fed to a `git log` command
   */
  public static String @NotNull [] formHashParameters(@NotNull Project project, @NotNull Collection<String> hashes) {
    List<String> parameters = new ArrayList<>();

    parameters.add(GitLogUtil.getNoWalkParameter(project));
    parameters.addAll(hashes);

    return ArrayUtilRt.toStringArray(parameters);
  }

  /**
   * Get current revision for the file under git in the current or specified branch.
   *
   * @param project  context project
   * @param filePath file path to the file which revision is to be retrieved
   * @param branch   name of branch or null if current branch wanted
   * @return revision number or null if the file is unversioned or new
   * @throws VcsException if there is a problem with running git
   */
  @Nullable
  public static VcsRevisionNumber getCurrentRevision(@NotNull Project project, @NotNull FilePath filePath,
                                                     @Nullable String branch) throws VcsException {
    filePath = VcsUtil.getLastCommitPath(project, filePath);
    GitLineHandler h = new GitLineHandler(project, GitUtil.getRootForFile(project, filePath), GitCommand.LOG);
    GitLogParser<GitLogRecord> parser = GitLogParser.createDefaultParser(project, HASH, COMMIT_TIME);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters(!StringUtil.isEmpty(branch) ? branch : "--all");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = Git.getInstance().runCommand(h).getOutputOrThrow();
    if (result.isEmpty()) {
      return null;
    }
    final GitLogRecord record = parser.parseOneRecord(result);
    if (record == null) {
      return null;
    }
    record.setUsedHandler(h);
    return new GitRevisionNumber(record.getHash(), record.getDate());
  }

  @Nullable
  public static VcsRevisionDescription getCurrentRevisionDescription(@NotNull Project project, @NotNull FilePath filePath)
    throws VcsException {
    filePath = VcsUtil.getLastCommitPath(project, filePath);
    GitLineHandler h = new GitLineHandler(project, GitUtil.getRootForFile(project, filePath), GitCommand.LOG);
    GitLogParser<GitLogRecord> parser = GitLogParser.createDefaultParser(project, HASH, COMMIT_TIME, AUTHOR_NAME, COMMITTER_NAME,
                                                                         SUBJECT, BODY, RAW_BODY);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters("--encoding=UTF-8");
    h.addParameters("--all");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = Git.getInstance().runCommand(h).getOutputOrThrow();
    if (result.isEmpty()) {
      return null;
    }
    final GitLogRecord record = parser.parseOneRecord(result);
    if (record == null) {
      return null;
    }
    record.setUsedHandler(h);

    final String author = Objects.equals(record.getAuthorName(), record.getCommitterName()) ? record.getAuthorName() :
                          record.getAuthorName() + " (" + record.getCommitterName() + ")";
    return new VcsRevisionDescriptionImpl(new GitRevisionNumber(record.getHash(), record.getDate()), record.getDate(), author,
                                          record.getFullMessage());
  }

  /**
   * Get current revision for the file under git.
   *
   * @param project  context project
   * @param filePath file path to the file which revision is to be retrieved
   * @return a revision number or null if the file is unversioned or new
   * @throws VcsException if there is problem with running git
   */
  @Nullable
  public static ItemLatestState getLastRevision(@NotNull Project project, @NotNull FilePath filePath) throws VcsException {
    GitRepository repository = GitUtil.getRepositoryForFile(project, filePath);
    VirtualFile root = repository.getRoot();
    GitBranch c = repository.getCurrentBranch();
    GitBranchTrackInfo info = c == null ? null : repository.getBranchTrackInfo(c.getName());
    GitBranch t = info == null ? null : info.getRemoteBranch();
    if (t == null) {
      return new ItemLatestState(getCurrentRevision(project, filePath, null), true, false);
    }
    filePath = VcsUtil.getLastCommitPath(project, filePath);
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser<GitLogFullRecord> parser = GitLogParser.createDefaultParser(project, GitLogParser.NameStatus.STATUS,
                                                                             HASH, COMMIT_TIME, PARENTS);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty(), "--name-status", t.getFullName());
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = Git.getInstance().runCommand(h).getOutputOrThrow();
    if (result.isEmpty()) {
      return null;
    }
    GitLogFullRecord record = parser.parseOneRecord(result);
    if (record == null) {
      return null;
    }
    final List<Change> changes = record.parseChanges(project, root);
    boolean exists = changes.isEmpty() || !FileStatus.DELETED.equals(changes.get(0).getFileStatus());
    record.setUsedHandler(h);
    return new ItemLatestState(new GitRevisionNumber(record.getHash(), record.getDate()), exists, false);
  }

  @Nullable
  public static String getNumberOfCommitsBetween(@NotNull GitRepository repository, @NotNull String from, @NotNull String to) {
    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REV_LIST);
    handler.addParameters("--count", from + ".." + to);
    handler.setSilent(true);
    try {
      return Git.getInstance().runCommand(handler).getOutputOrThrow().trim();
    }
    catch (VcsException e) {
      LOG.debug(e.getMessage());
      return null;
    }
  }

  @Nullable
  public static GitRevisionNumber getMergeBase(@NotNull Project project, @NotNull VirtualFile root, @NotNull String first,
                                               @NotNull String second) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.MERGE_BASE);
    h.setSilent(true);
    h.addParameters(first, second);
    GitCommandResult result = Git.getInstance().runCommand(h);
    if (!result.success()) return null;
    String output = result.getOutputAsJoinedString().trim();
    if (output.isEmpty()) return null;
    return GitRevisionNumber.resolve(project, root, output);
  }

  /**
   * @deprecated use {@link GitHistoryUtils#collectTimedCommits(Project, VirtualFile, String...)} or methods from {@link GitFileHistory}
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public static List<Pair<SHAHash, Date>> onlyHashesHistory(@NotNull Project project,
                                                            @NotNull FilePath path,
                                                            @NotNull VirtualFile root,
                                                            String... parameters)
    throws VcsException {
    // adjust path using change manager
    path = VcsUtil.getLastCommitPath(project, path);
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser<GitLogRecord> parser = GitLogParser.createDefaultParser(project, HASH, COMMIT_TIME);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();

    final List<Pair<SHAHash, Date>> rc = new ArrayList<>();
    for (GitLogRecord record : parser.parse(output)) {
      record.setUsedHandler(h);
      rc.add(Pair.create(new SHAHash(record.getHash()), record.getDate()));
    }
    return rc;
  }
}
