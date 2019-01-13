// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.history.browser.SHAHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * A collection of methods for retrieving history information from native Git.
 */
public class GitHistoryUtils {
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
                                 @NotNull String... parameters) throws VcsException {
    GitLogUtil.readFullDetails(project, root, commitConsumer, true, true, false, parameters);
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
                                      @NotNull String... parameters) throws VcsException {
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
   */
  @SuppressWarnings("unused")
  public static List<? extends TimedVcsCommit> collectTimedCommits(@NotNull Project project,
                                                                   @NotNull VirtualFile root,
                                                                   @NotNull String... parameters) throws VcsException {
    List<TimedVcsCommit> commits = ContainerUtil.newArrayList();
    loadTimedCommits(project, root, commits::add, parameters);
    return commits;
  }

  /**
   * Collect commit information in a form of {@link VcsCommitMetadata} (containing commit details, but no changes)
   * for the specified hashes or references.
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
                                                                         @NotNull String... hashes)
    throws VcsException {
    List<? extends VcsCommitMetadata> result = GitLogUtil.collectShortDetails(project, GitVcs.getInstance(project), root,
                                                                              Arrays.asList(hashes));
    if (result.size() != hashes.length) return null;
    return result;
  }

  /**
   * Collect commit information in a form of {@link GitCommit} (containing commit details and changes to commit parents)
   * in the repository using `git log` command.
   * <br/>
   * Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   * and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.
   *
   * @param project    context project
   * @param root       git repository root
   * @param parameters additional parameters for `git log` command
   * @return a list of {@link GitCommit}
   * @throws VcsException if there is a problem with running git
   */
  @NotNull
  public static List<GitCommit> history(@NotNull Project project, @NotNull VirtualFile root, String... parameters)
    throws VcsException {
    VcsLogObjectsFactory factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    return GitLogUtil.collectFullDetails(project, root, parameters);
  }

  /**
   * Create a proper list of parameters for `git log` command from a list of hashes.
   *
   * @param vcs    an instance of {@link GitVcs} class for the repository, could be obtained from the
   *               corresponding {@link git4idea.repo.GitRepository} or from a project by calling {@code GitVcs.getInstance(project)}
   * @param hashes a list of hashes to call `git log` for
   * @return a list of parameters that could be fed to a `git log` command
   * @throws VcsException if there is a problem with running git
   */
  @NotNull
  public static String[] formHashParameters(@NotNull GitVcs vcs, @NotNull Collection<String> hashes) {
    List<String> parameters = ContainerUtil.newArrayList();

    parameters.add(GitLogUtil.getNoWalkParameter(vcs));
    parameters.addAll(hashes);

    return ArrayUtil.toStringArray(parameters);
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
    GitLineHandler h = new GitLineHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters(!StringUtil.isEmpty(branch) ? branch : "--all");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = Git.getInstance().runCommand(h).getOutputOrThrow();
    if (result.length() == 0) {
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
    GitLineHandler h = new GitLineHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME, AUTHOR_NAME, COMMITTER_NAME, SUBJECT, BODY, RAW_BODY);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters("--encoding=UTF-8");
    h.addParameters("--all");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = Git.getInstance().runCommand(h).getOutputOrThrow();
    if (result.length() == 0) {
      return null;
    }
    final GitLogRecord record = parser.parseOneRecord(result);
    if (record == null) {
      return null;
    }
    record.setUsedHandler(h);

    final String author = Comparing.equal(record.getAuthorName(), record.getCommitterName()) ? record.getAuthorName() :
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
    VirtualFile root = GitUtil.getGitRoot(filePath);
    GitBranch c = GitBranchUtil.getCurrentBranch(project, root);
    GitBranch t = c == null ? null : GitBranchUtil.tracked(project, root, c.getName());
    if (t == null) {
      return new ItemLatestState(getCurrentRevision(project, filePath, null), true, false);
    }
    filePath = VcsUtil.getLastCommitPath(project, filePath);
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, COMMIT_TIME, PARENTS);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty(), "--name-status", t.getFullName());
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = Git.getInstance().runCommand(h).getOutputOrThrow();
    if (result.length() == 0) {
      return null;
    }
    GitLogRecord record = parser.parseOneRecord(result);
    if (record == null) {
      return null;
    }
    final List<Change> changes = record.parseChanges(project, root);
    boolean exists = changes.isEmpty() || !FileStatus.DELETED.equals(changes.get(0).getFileStatus());
    record.setUsedHandler(h);
    return new ItemLatestState(new GitRevisionNumber(record.getHash(), record.getDate()), exists, false);
  }

  @Nullable
  public static GitRevisionNumber getMergeBase(@NotNull Project project, @NotNull VirtualFile root, @NotNull String first,
                                               @NotNull String second)
    throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.MERGE_BASE);
    h.setSilent(true);
    h.addParameters(first, second);
    String output = Git.getInstance().runCommand(h).getOutputOrThrow().trim();
    if (output.length() == 0) {
      return null;
    }
    else {
      return GitRevisionNumber.resolve(project, root, output);
    }
  }

  public static long getAuthorTime(@NotNull Project project, @NotNull VirtualFile root, @NotNull String commitsId) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.SHOW);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, AUTHOR_TIME);
    h.setSilent(true);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(commitsId);

    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    GitLogRecord logRecord = parser.parseOneRecord(output);
    if (logRecord == null) throw new VcsException("Can not parse log output \"" + output + "\"");
    return logRecord.getAuthorTimeStamp();
  }

  /**
   * Get name of the file in the last commit. If file was renamed, returns the previous name.
   *
   * @param project the context project
   * @param path    the path to check
   * @return the name of file in the last commit or argument
   * @deprecated use {@link VcsUtil#getLastCommitPath(Project, FilePath)}
   */
  @NotNull
  @Deprecated
  public static FilePath getLastCommitName(@NotNull Project project, @NotNull FilePath path) {
    return VcsUtil.getLastCommitPath(project, path);
  }

  /**
   * @deprecated use {@link GitHistoryUtils#collectTimedCommits(Project, VirtualFile, String...)}
   */
  @Deprecated
  @SuppressWarnings("unused")
  @NotNull
  public static List<Pair<SHAHash, Date>> onlyHashesHistory(@NotNull Project project, @NotNull FilePath path, String... parameters)
    throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    return onlyHashesHistory(project, path, root, parameters);
  }

  /**
   * @deprecated use {@link GitHistoryUtils#collectTimedCommits(Project, VirtualFile, String...)}
   */
  @Deprecated
  @NotNull
  public static List<Pair<SHAHash, Date>> onlyHashesHistory(@NotNull Project project,
                                                            @NotNull FilePath path,
                                                            @NotNull VirtualFile root,
                                                            String... parameters)
    throws VcsException {
    // adjust path using change manager
    path = VcsUtil.getLastCommitPath(project, path);
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME);
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

  /**
   * @deprecated use {@link GitHistoryUtils#collectCommitsMetadata(Project, VirtualFile, String...)} instead.
   */
  @Deprecated
  public static List<? extends VcsCommitMetadata> readLastCommits(@NotNull Project project,
                                                                  @NotNull VirtualFile root,
                                                                  @NotNull String... hashes) throws VcsException {
    return collectCommitsMetadata(project, root, hashes);
  }
}
