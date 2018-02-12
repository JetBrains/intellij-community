/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.history;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionDescriptionImpl;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogObjectsFactory;
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
   * Get current revision for the file under git in the current or specified branch.
   *
   * @param project  a project
   * @param filePath file path to the file which revision is to be retrieved.
   * @param branch   name of branch or null if current branch wanted.
   * @return revision number or null if the file is unversioned or new.
   * @throws VcsException if there is a problem with running git.
   */
  @Nullable
  public static VcsRevisionNumber getCurrentRevision(@NotNull Project project, @NotNull FilePath filePath,
                                                     @Nullable String branch) throws VcsException {
    filePath = getLastCommitName(project, filePath);
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
    filePath = getLastCommitName(project, filePath);
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
   * Get current revision for the file under git
   *
   * @param project  a project
   * @param filePath a file path
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
    filePath = getLastCommitName(project, filePath);
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
  public static List<VcsCommitMetadata> readLastCommits(@NotNull Project project,
                                                        @NotNull VirtualFile root,
                                                        @NotNull String... refs)
    throws VcsException {
    final VcsLogObjectsFactory factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return null;
    }

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, COMMIT_TIME, SUBJECT, AUTHOR_NAME,
                                           AUTHOR_EMAIL, RAW_BODY, COMMITTER_NAME, COMMITTER_EMAIL, AUTHOR_TIME);

    h.setSilent(true);
    // git show can show either -p, or --name-status, or --name-only, but we need nothing, just details => using git log --no-walk
    h.addParameters("--no-walk");
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(refs);
    h.endOptions();

    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    List<GitLogRecord> records = parser.parse(output);
    if (records.size() != refs.length) return null;

    return ContainerUtil.map(records,
                             record -> factory.createCommitMetadata(factory.createHash(record.getHash()),
                                                                    GitLogUtil.getParentHashes(factory, record),
                                                                    record.getCommitTime(),
                                                                    root, record.getSubject(), record.getAuthorName(),
                                                                    record.getAuthorEmail(),
                                                                    record.getFullMessage(), record.getCommitterName(),
                                                                    record.getCommitterEmail(),
                                                                    record.getAuthorTimeStamp()));
  }

  /**
   * @deprecated To remove in IDEA 17
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
   * @deprecated To remove in IDEA 17
   */
  @Deprecated
  @NotNull
  public static List<Pair<SHAHash, Date>> onlyHashesHistory(@NotNull Project project,
                                                            @NotNull FilePath path,
                                                            @NotNull VirtualFile root,
                                                            String... parameters)
    throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
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
   * <p>Get & parse git log detailed output with commits, their parents and their changes.</p>
   * <p>
   * <p>Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   * and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.</p>
   */
  @NotNull
  public static List<GitCommit> history(@NotNull Project project, @NotNull VirtualFile root, String... parameters)
    throws VcsException {
    final VcsLogObjectsFactory factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    return GitLogUtil.collectFullDetails(project, root, parameters);
  }

  @NotNull
  public static String[] formHashParameters(@NotNull GitVcs vcs, @NotNull Collection<String> hashes) {
    List<String> parameters = ContainerUtil.newArrayList();

    parameters.add(GitLogUtil.getNoWalkParameter(vcs));
    parameters.addAll(hashes);

    return ArrayUtil.toStringArray(parameters);
  }

  public static void loadDetails(@NotNull Project project,
                                 @NotNull VirtualFile root,
                                 @NotNull Consumer<? super GitCommit> commitConsumer,
                                 @NotNull String... parameters) throws VcsException {
    GitLogUtil.readFullDetails(project, root, commitConsumer, parameters);
  }

  public static long getAuthorTime(@NotNull Project project, @NotNull FilePath path, @NotNull String commitsId) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
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
   */
  public static FilePath getLastCommitName(@NotNull Project project, FilePath path) {
    if (project.isDefault()) return path;
    final ChangeListManager changeManager = ChangeListManager.getInstance(project);
    final Change change = changeManager.getChange(path);
    if (change != null && change.getType() == Change.Type.MOVED) {
      // GitContentRevision r = (GitContentRevision)change.getBeforeRevision();
      assert change.getBeforeRevision() != null : "Move change always have beforeRevision";
      path = change.getBeforeRevision().getFile();
    }
    return path;
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
}
