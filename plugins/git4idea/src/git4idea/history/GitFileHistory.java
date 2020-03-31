// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.impl.VcsFileStatusInfo;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * An implementation of file history algorithm with renames detection.
 * <p>
 * 'git log --follow' does detect renames, but it has a bug - merge commits aren't handled properly: they just disappear from the history.
 * See http://kerneltrap.org/mailarchive/git/2009/1/30/4861054 and the whole thread about that: --follow is buggy, but maybe it won't be fixed.
 * To get the whole history through renames we do the following:
 * 1. 'git log <file>' - and we get the history since the first rename, if there was one.
 * 2. 'git show -M --follow --name-status <first_commit_id> -- <file>'
 * where <first_commit_id> is the hash of the first commit in the history we got in #1.
 * With this command we get the rename-detection-friendly information about the first commit of the given file history.
 * (by specifying the <file> we filter out other changes in that commit; but in that case rename detection requires '--follow' to work,
 * that's safe for one commit though)
 * If the first commit was ADDING the file, then there were no renames with this file, we have the full history.
 * But if the first commit was RENAMING the file, we are going to query for the history before rename.
 * Now we have the previous name of the file:
 * <p>
 * ~/sandbox/git # git show --oneline --name-status -M 4185b97
 * 4185b97 renamed a to b
 * R100    a       b
 * <p>
 * 3. 'git log <rename_commit_id> -- <previous_file_name>' - get the history of a before the given commit.
 * We need to specify <rename_commit_id> here, because <previous_file_name> could have some new history, which has nothing common with our <file>.
 * Then we repeat 2 and 3 until the first commit is ADDING the file, not RENAMING it.
 * <p>
 * TODO: handle multiple repositories configuration: a file can be moved from one repo to another
 */
public class GitFileHistory {
  private static final Logger LOG = Logger.getInstance(GitFileHistory.class);

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final FilePath myPath;
  @NotNull private final VcsRevisionNumber myStartingRevision;

  private GitFileHistory(@NotNull Project project, @NotNull VirtualFile root, @NotNull FilePath path, @NotNull VcsRevisionNumber revision) {
    myProject = project;
    myRoot = root;
    myPath = VcsUtil.getLastCommitPath(myProject, path);
    myStartingRevision = revision;
  }

  private void load(@NotNull Consumer<? super GitFileRevision> consumer,
                    @NotNull Consumer<? super VcsException> exceptionConsumer,
                    String... parameters) {
    GitLogParser<GitLogFullRecord> logParser = GitLogParser.createDefaultParser(myProject, GitLogParser.NameStatus.STATUS,
                                                                                HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_EMAIL,
                                                                                COMMITTER_NAME,
                                                                                COMMITTER_EMAIL, PARENTS,
                                                                                SUBJECT, BODY, RAW_BODY, AUTHOR_TIME);
    GitLogRecordConsumer recordConsumer = new GitLogRecordConsumer(consumer);

    String firstCommitParent = myStartingRevision.asString();
    FilePath currentPath = myPath;

    while (currentPath != null && firstCommitParent != null) {
      recordConsumer.reset(currentPath);

      GitLineHandler handler = createLogHandler(logParser, currentPath, firstCommitParent, parameters);
      GitLogOutputSplitter<GitLogFullRecord> splitter = new GitLogOutputSplitter<>(handler, logParser, recordConsumer);

      Git.getInstance().runCommandWithoutCollectingOutput(handler);
      if (splitter.hasErrors()) {
        return;
      }

      try {
        String firstCommit = recordConsumer.getFirstCommit();
        if (firstCommit == null) return;
        Pair<String, FilePath> firstCommitParentAndPath = getFirstCommitParentAndPathIfRename(firstCommit, currentPath);
        if (firstCommitParentAndPath == null) {
          return;
        }
        currentPath = firstCommitParentAndPath.second;
        firstCommitParent = firstCommitParentAndPath.first;
      }
      catch (VcsException e) {
        LOG.warn("Tried to get first commit rename path", e);
        exceptionConsumer.consume(e);
        return;
      }
    }
  }

  /**
   * Gets info of the given commit and checks if a file was renamed there.
   * If yes, returns the older file path, which file was renamed from and a parent commit hash as a string.
   * If it's not a rename, returns null.
   */
  @Nullable
  private Pair<String, FilePath> getFirstCommitParentAndPathIfRename(@NotNull String commit,
                                                                     @NotNull FilePath filePath) throws VcsException {
    // 'git show -M --name-status <commit hash>' returns the information about commit and detects renames.
    // NB: we can't specify the filepath, because then rename detection will work only with the '--follow' option, which we don't wanna use.
    GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.SHOW);
    GitLogParser<GitLogFullRecord> parser = GitLogParser.createDefaultParser(myProject, GitLogParser.NameStatus.STATUS,
                                                                             HASH, COMMIT_TIME, PARENTS);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "-m", "--follow", "--name-status", parser.getPretty(), "--encoding=UTF-8", commit);
    h.endOptions();
    h.addRelativePaths(filePath);

    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    List<GitLogFullRecord> records = parser.parse(output);

    if (records.isEmpty()) return null;
    // we have information about all changed files of the commit. Extracting information about the file we need.
    for (int i = 0; i < records.size(); i++) {
      GitLogFullRecord record = records.get(i);
      List<Change> changes = record.parseChanges(myProject, myRoot);
      for (Change change : changes) {
        if ((change.isMoved() || change.isRenamed()) && filePath.equals(Objects.requireNonNull(change.getAfterRevision()).getFile())) {
          String[] parents = record.getParentsHashes();
          String parent = parents.length > 0 ? parents[i] : null;
          return Pair.create(parent, Objects.requireNonNull(change.getBeforeRevision()).getFile());
        }
      }
    }
    return null;
  }

  @NotNull
  private GitLineHandler createLogHandler(@NotNull GitLogParser parser,
                                          @NotNull FilePath path,
                                          @NotNull String lastCommit,
                                          String... parameters) {
    GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.LOG);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8", lastCommit);
    if (GitVersionSpecialty.FULL_HISTORY_SIMPLIFY_MERGES_WORKS_CORRECTLY.existsIn(myProject) && Registry.is("git.file.history.full")) {
      h.addParameters("--full-history", "--simplify-merges");
    }
    if (parameters != null && parameters.length > 0) {
      h.addParameters(parameters);
    }
    h.endOptions();
    h.addRelativePaths(path);
    return h;
  }

  /**
   * Get history for the file starting from specific revision and feed it to the consumer.
   *
   * @param project           Context project.
   * @param path              FilePath which history is queried.
   * @param startingFrom      Revision from which to start file history, when null history is started from HEAD revision.
   * @param consumer          This consumer is notified ({@link Consumer#consume(Object)} when new history records are retrieved.
   * @param exceptionConsumer This consumer is notified in case of error while executing git command.
   * @param parameters        Optional parameters which will be added to the git log command just before the path.
   */
  public static void loadHistory(@NotNull Project project,
                                 @NotNull FilePath path,
                                 @Nullable VcsRevisionNumber startingFrom,
                                 @NotNull Consumer<? super GitFileRevision> consumer,
                                 @NotNull Consumer<? super VcsException> exceptionConsumer,
                                 String... parameters) {
    try {
      VirtualFile repositoryRoot = GitUtil.getRootForFile(project, path);
      VcsRevisionNumber revision = startingFrom == null ? GitRevisionNumber.HEAD : startingFrom;
      new GitFileHistory(project, repositoryRoot, path, revision).load(consumer, exceptionConsumer, parameters);
    }
    catch (VcsException e) {
      exceptionConsumer.consume(e);
    }
  }

  /**
   * Get history for the file starting from specific revision.
   *
   * @param project      the context project
   * @param path         the file path
   * @param startingFrom revision from which to start file history
   * @param parameters   optional parameters which will be added to the git log command just before the path
   * @return list of the revisions
   * @throws VcsException if there is problem with running git
   */
  @NotNull
  public static List<VcsFileRevision> collectHistoryForRevision(@NotNull Project project,
                                                                @NotNull FilePath path,
                                                                @NotNull VcsRevisionNumber startingFrom,
                                                                String... parameters) throws VcsException {
    List<VcsFileRevision> revisions = new ArrayList<>();
    List<VcsException> exceptions = new ArrayList<>();

    loadHistory(project, path, startingFrom, revisions::add, exceptions::add, parameters);

    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
    return revisions;
  }

  /**
   * Get history for the file.
   *
   * @param project    the context project
   * @param path       the file path
   * @param parameters optional parameters which will be added to the git log command just before the path
   * @return list of the revisions
   * @throws VcsException if there is problem with running git
   */
  @NotNull
  public static List<VcsFileRevision> collectHistory(@NotNull Project project, @NotNull FilePath path, String... parameters)
    throws VcsException {
    return collectHistoryForRevision(project, path, GitRevisionNumber.HEAD, parameters);
  }

  private class GitLogRecordConsumer implements Consumer<GitLogFullRecord> {
    @NotNull private final AtomicBoolean mySkipFurtherOutput = new AtomicBoolean();
    @NotNull private final AtomicReference<String> myFirstCommit = new AtomicReference<>();
    @NotNull private final AtomicReference<FilePath> myCurrentPath = new AtomicReference<>();
    @NotNull private final Consumer<? super GitFileRevision> myRevisionConsumer;

    GitLogRecordConsumer(@NotNull Consumer<? super GitFileRevision> revisionConsumer) {
      myRevisionConsumer = revisionConsumer;
    }

    public void reset(@NotNull FilePath path) {
      myCurrentPath.set(path);
      mySkipFurtherOutput.set(false);
    }

    @Override
    public void consume(@NotNull GitLogFullRecord record) {
      if (mySkipFurtherOutput.get()) {
        return;
      }

      myFirstCommit.set(record.getHash());

      myRevisionConsumer.consume(createGitFileRevision(record));
      List<? extends VcsFileStatusInfo> statusInfos = record.getStatusInfos();
      if (statusInfos.isEmpty()) {
        // can safely be empty, for example, for simple merge commits that don't change anything.
        return;
      }
      if (statusInfos.get(0).getType() == Change.Type.NEW && !myPath.isDirectory()) {
        mySkipFurtherOutput.set(true);
      }
    }

    @NotNull
    private GitFileRevision createGitFileRevision(@NotNull GitLogFullRecord record) {
      GitRevisionNumber revision = new GitRevisionNumber(record.getHash(), record.getDate());
      FilePath revisionPath = getRevisionPath(record);
      Couple<String> authorPair = Couple.of(record.getAuthorName(), record.getAuthorEmail());
      Couple<String> committerPair = Couple.of(record.getCommitterName(), record.getCommitterEmail());
      Collection<String> parents = Arrays.asList(record.getParentsHashes());
      List<? extends VcsFileStatusInfo> statusInfos = record.getStatusInfos();
      boolean deleted = !statusInfos.isEmpty() && statusInfos.get(0).getType() == Change.Type.DELETED;
      return new GitFileRevision(myProject, myRoot, revisionPath, revision, Couple.of(authorPair, committerPair),
                                 record.getFullMessage(),
                                 null, new Date(record.getAuthorTimeStamp()), parents, deleted);
    }

    @NotNull
    private FilePath getRevisionPath(@NotNull GitLogFullRecord record) {
      List<FilePath> paths = record.getFilePaths(myRoot);
      if (paths.size() > 0) {
        return paths.get(0);
      }
      // no paths are shown for merge commits, so we're using the saved path we're inspecting now
      return myCurrentPath.get();
    }

    @Nullable
    public String getFirstCommit() {
      return myFirstCommit.get();
    }
  }
}
