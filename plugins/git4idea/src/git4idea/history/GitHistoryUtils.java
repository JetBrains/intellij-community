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
package git4idea.history;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionDescriptionImpl;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.*;
import git4idea.config.GitConfigUtil;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.browser.SymbolicRefs;
import git4idea.history.browser.SymbolicRefsI;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.history.wholeTree.CommitHashPlusParents;
import git4idea.history.wholeTree.GitCommitsSequentialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * A collection of methods for retrieving history information from native Git.
 */
public class GitHistoryUtils {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.GitHistoryUtils");

  private GitHistoryUtils() {
  }

  public static long getHeadTs(final Project project, FilePath filePath) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters("HEAD");
    h.endOptions();
    String result = h.run();
    if (result.length() == 0) {
      return -1;
    }
    final GitLogRecord record = parser.parseOneRecord(result);
    if (record == null) {
      return -1;
    }
    record.setUsedHandler(h);
    return record.getDate().getTime();
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
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters(!StringUtil.isEmpty(branch) ? branch : "--all");
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
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
  public static VcsRevisionDescription getCurrentRevisionDescription(final Project project, FilePath filePath, @Nullable String branch) throws VcsException {
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME, AUTHOR_NAME, COMMITTER_NAME, SUBJECT, BODY, RAW_BODY);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    if (branch != null && !branch.isEmpty()) {
      h.addParameters(branch);
    } else {
      h.addParameters("--all");
    }
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
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
  public static ItemLatestState getLastRevision(final Project project, FilePath filePath) throws VcsException {
    VirtualFile root = GitUtil.getGitRoot(filePath);
    GitBranch c = GitBranchUtil.getCurrentBranch(project, root);
    GitBranch t = c == null ? null : GitBranchUtil.tracked(project, root, c.getName());
    if (t == null) {
      return new ItemLatestState(getCurrentRevision(project, filePath, null), true, false);
    }
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, COMMIT_TIME, PARENTS);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty(), "--name-status", t.getFullName());
    h.endOptions();
    h.addRelativePaths(filePath);
    String result = h.run();
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

  public static void dumpFullHistory(final Project project, VirtualFile root, final String outFilePath) throws VcsException {
    if (! GitUtil.isGitRoot(new File(root.getPath()))) throw new VcsException("Path " + root.getPath() + " is not git repository root");

    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setSilent(true);
    h.addParameters("HEAD", "--branches", "--remotes", "--tags",
                    "--pretty=format:%H%x20%ct%x0A", "--date-order", "--reverse", "--encoding=UTF-8", "--full-history",
                    "--sparse");
    h.endOptions();

    final OutputStream[] stream = new OutputStream[1];
    try {
      stream[0] = new BufferedOutputStream(new FileOutputStream(outFilePath, false));
      final Semaphore semaphore = new Semaphore();
      final VcsException[] ioExceptions = new VcsException[1];
      h.addLineListener(new GitLineHandlerListener() {
        @Override
        public void onLineAvailable(String line, Key outputType) {
          if (line.length() == 0) return;
          try {
            GitCommitsSequentialIndex.parseRecord(line);
            stream[0].write((line + '\n').getBytes("UTF-8"));
          }
          catch (IOException e) {
            ioExceptions[0] = new VcsException(e);
            h.cancel();
            semaphore.up();
          } catch (ProcessCanceledException e) {
            h.cancel();
            semaphore.up();
          }
          catch (VcsException e) {
            ioExceptions[0] = e;
            h.cancel();
            semaphore.up();
          }
        }
        @Override
        public void processTerminated(int exitCode) {
          semaphore.up();
        }
        @Override
        public void startFailed(Throwable exception) {
          semaphore.up();
        }
      });
      semaphore.down();
      h.start();
      semaphore.waitFor();
      if (ioExceptions[0] != null) {
        throw ioExceptions[0];
      }
    }
    catch (FileNotFoundException e) {
      throw new VcsException(e);
    }
    finally {
      try {
        if (stream[0] != null) {
          stream[0].close();
        }
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
    }
    File file = new File(outFilePath);
    if (! file.exists() || file.length() == 0) throw new VcsException("Short repository history not loaded");
  }

  /*
   === Smart full log with renames ===
   'git log --follow' does detect renames, but it has a bug - merge commits aren't handled properly: they just dissapear from the history.
   See http://kerneltrap.org/mailarchive/git/2009/1/30/4861054 and the whole thread about that: --follow is buggy, but maybe it won't be fixed.
   To get the whole history through renames we do the following:
   1. 'git log <file>' - and we get the history since the first rename, if there was one.
   2. 'git show -M --follow --name-status <first_commit_id> -- <file>'
      where <first_commit_id> is the hash of the first commit in the history we got in #1.
      With this command we get the rename-detection-friendly information about the first commit of the given file history.
      (by specifying the <file> we filter out other changes in that commit; but in that case rename detection requires '--follow' to work,
      that's safe for one commit though)
      If the first commit was ADDING the file, then there were no renames with this file, we have the full history.
      But if the first commit was RENAMING the file, we are going to query for the history before rename.
      Now we have the previous name of the file:

        ~/sandbox/git # git show --oneline --name-status -M 4185b97
        4185b97 renamed a to b
        R100    a       b

   3. 'git log <rename_commit_id> -- <previous_file_name>' - get the history of a before the given commit.
      We need to specify <rename_commit_id> here, because <previous_file_name> could have some new history, which has nothing common with our <file>.
      Then we repeat 2 and 3 until the first commit is ADDING the file, not RENAMING it.

    TODO: handle multiple repositories configuration: a file can be moved from one repo to another
   */

  /**
   * Retrieves the history of the file, including renames.
   * @param project
   * @param path              FilePath which history is queried.
   * @param root              Git root - optional: if this is null, then git root will be detected automatically.
   * @param consumer          This consumer is notified ({@link Consumer#consume(Object)} when new history records are retrieved.
   * @param exceptionConsumer This consumer is notified in case of error while executing git command.
   * @param parameters        Optional parameters which will be added to the git log command just before the path.
   */
  public static void history(final Project project, FilePath path, @Nullable VirtualFile root, final Consumer<GitFileRevision> consumer,
                             final Consumer<VcsException> exceptionConsumer, String... parameters) {
    // adjust path using change manager
    final FilePath filePath = getLastCommitName(project, path);
    final VirtualFile finalRoot;
    try {
      finalRoot = (root == null ? GitUtil.getGitRoot(filePath) : root);
    }
    catch (VcsException e) {
      exceptionConsumer.consume(e);
      return;
    }
    final GitLogParser logParser = new GitLogParser(project, GitLogParser.NameStatus.STATUS,
                                                    HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL, PARENTS,
                                                    SUBJECT, BODY, RAW_BODY, AUTHOR_TIME);

    final AtomicReference<String> firstCommit = new AtomicReference<String>("HEAD");
    final AtomicReference<String> firstCommitParent = new AtomicReference<String>("HEAD");
    final AtomicReference<FilePath> currentPath = new AtomicReference<FilePath>(filePath);
    final AtomicReference<GitLineHandler> logHandler = new AtomicReference<GitLineHandler>();
    final AtomicBoolean skipFurtherOutput = new AtomicBoolean();

    final Consumer<GitLogRecord> resultAdapter = new Consumer<GitLogRecord>() {
      public void consume(GitLogRecord record) {
        if (skipFurtherOutput.get()) {
          return;
        }
        if (record == null) {
          exceptionConsumer.consume(new VcsException("revision details are null."));
          return;
        }
        record.setUsedHandler(logHandler.get());
        final GitRevisionNumber revision = new GitRevisionNumber(record.getHash(), record.getDate());
        firstCommit.set(record.getHash());
        final String[] parentHashes = record.getParentsHashes();
        if (parentHashes == null || parentHashes.length < 1) {
          firstCommitParent.set(null);
        }
        else {
          firstCommitParent.set(parentHashes[0]);
        }
        final String message = record.getFullMessage();

        FilePath revisionPath;
        try {
          final List<FilePath> paths = record.getFilePaths(finalRoot);
          if (paths.size() > 0) {
            revisionPath = paths.get(0);
          }
          else {
            // no paths are shown for merge commits, so we're using the saved path we're inspecting now
            revisionPath = currentPath.get();
          }

          final Pair<String, String> authorPair = Pair.create(record.getAuthorName(), record.getAuthorEmail());
          final Pair<String, String> committerPair =
            record.getCommitterName() == null ? null : Pair.create(record.getCommitterName(), record.getCommitterEmail());
          Collection<String> parents = parentHashes == null ? Collections.<String>emptyList() : Arrays.asList(parentHashes);
          consumer.consume(new GitFileRevision(project, revisionPath, revision, Pair.create(authorPair, committerPair), message, null,
                                               new Date(record.getAuthorTimeStamp()), parents));
          List<GitLogStatusInfo> statusInfos = record.getStatusInfos();
          if (statusInfos.isEmpty()) {
            // can safely be empty, for example, for simple merge commits that don't change anything.
            return;
          }
          if (statusInfos.get(0).getType() == GitChangeType.ADDED && !filePath.isDirectory()) {
            skipFurtherOutput.set(true);
          }
        }
        catch (VcsException e) {
          exceptionConsumer.consume(e);
        }
      }
    };

    final AtomicBoolean criticalFailure = new AtomicBoolean();
    while (currentPath.get() != null && firstCommitParent.get() != null) {
      logHandler.set(getLogHandler(project, finalRoot, logParser, currentPath.get(), firstCommitParent.get(), parameters));
      final MyTokenAccumulator accumulator = new MyTokenAccumulator(logParser);
      final Semaphore semaphore = new Semaphore();

      logHandler.get().addLineListener(new GitLineHandlerAdapter() {
        @Override
        public void onLineAvailable(String line, Key outputType) {
          final GitLogRecord record = accumulator.acceptLine(line);
          if (record != null) {
            resultAdapter.consume(record);
          }
        }

        @Override
        public void startFailed(Throwable exception) {
          //noinspection ThrowableInstanceNeverThrown
          try {
            exceptionConsumer.consume(new VcsException(exception));
          } finally {
            criticalFailure.set(true);
            semaphore.up();
          }
        }

        @Override
        public void processTerminated(int exitCode) {
          try {
            super.processTerminated(exitCode);
            final GitLogRecord record = accumulator.processLast();
            if (record != null) {
              resultAdapter.consume(record);
            }
          } 
          catch (Throwable t) {
            LOG.error(t);
            exceptionConsumer.consume(new VcsException("Internal error " + t.getMessage(), t));
            criticalFailure.set(true);
          }
          finally {
            semaphore.up();
          }
        }
      });
      semaphore.down();
      logHandler.get().start();
      semaphore.waitFor();
      if (criticalFailure.get()) {
        return;
      }

      try {
        FilePath firstCommitRenamePath;
        firstCommitRenamePath = getFirstCommitRenamePath(project, finalRoot, firstCommit.get(), currentPath.get());
        currentPath.set(firstCommitRenamePath);
        skipFurtherOutput.set(false);
      }
      catch (VcsException e) {
        LOG.warn("Tried to get first commit rename path", e);
        exceptionConsumer.consume(e);
        return;
      }
    }

  }

  private static GitLineHandler getLogHandler(Project project, VirtualFile root, GitLogParser parser, FilePath path, String lastCommit, String... parameters) {
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8", lastCommit);
    if (parameters != null && parameters.length > 0) {
      h.addParameters(parameters);
    }
    h.endOptions();
    h.addRelativePaths(path);
    return h;
  }

  /**
   * Gets info of the given commit and checks if it was a RENAME.
   * If yes, returns the older file path, which file was renamed from.
   * If it's not a rename, returns null.
   */
  @Nullable
  private static FilePath getFirstCommitRenamePath(Project project, VirtualFile root, String commit, FilePath filePath) throws VcsException {
    // 'git show -M --name-status <commit hash>' returns the information about commit and detects renames.
    // NB: we can't specify the filepath, because then rename detection will work only with the '--follow' option, which we don't wanna use.
    final GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    final GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, COMMIT_TIME, PARENTS);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "--name-status", parser.getPretty(), "--encoding=UTF-8", commit);
    h.endOptions();
    final String output = h.run();
    final List<GitLogRecord> records = parser.parse(output);

    if (records.isEmpty()) return null;
    // we have information about all changed files of the commit. Extracting information about the file we need.
    final List<Change> changes = records.get(0).parseChanges(project, root);
    for (Change change : changes) {
      if ((change.isMoved() || change.isRenamed()) && filePath.equals(change.getAfterRevision().getFile())) {
        return change.getBeforeRevision().getFile();
      }
    }
    return null;
  }

  public static List<? extends VcsShortCommitDetails> readAllMiniDetails(final Project project, final VirtualFile root) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, AUTHOR_NAME,
                                           AUTHOR_EMAIL, AUTHOR_TIME, SUBJECT);
    h.setStdoutSuppressed(true);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters("HEAD", "--branches", "--remotes", "--tags");
    h.addParameters("--full-history", "--sparse");
    h.endOptions();

    String output = h.run();

    List<GitLogRecord> records = parser.parse(output);

    return ContainerUtil.mapNotNull(records, new Function<GitLogRecord, VcsShortCommitDetails>() {
      @Override
      public VcsShortCommitDetails fun(GitLogRecord record) {
        List<Hash> parents = new SmartList<Hash>();
        for (String parent : record.getParentsHashes()) {
          parents.add(HashImpl.build(parent));
        }

        return vcsObjectsFactory(project).createShortDetails(HashImpl.build(record.getHash()), parents, record.getAuthorTimeStamp(), root,
                                             record.getSubject(), record.getAuthorName(), record.getAuthorEmail());
      }
    });
  }

  public static List<? extends VcsShortCommitDetails> readMiniDetails(final Project project, final VirtualFile root, List<String> hashes) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, AUTHOR_NAME,
                                           AUTHOR_EMAIL, COMMIT_TIME, SUBJECT);
    h.setStdoutSuppressed(true);
    // git show can show either -p, or --name-status, or --name-only, but we need nothing, just details => using git log --no-walk
    h.addParameters("--no-walk");
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(new ArrayList<String>(hashes));

    String output = h.run();
    List<GitLogRecord> records = parser.parse(output);

    return ContainerUtil.map(records, new Function<GitLogRecord, VcsShortCommitDetails>() {
      @Override
      public VcsShortCommitDetails fun(GitLogRecord record) {
        List<Hash> parents = new SmartList<Hash>();
        for (String parent : record.getParentsHashes()) {
          parents.add(HashImpl.build(parent));
        }
        return vcsObjectsFactory(project).createShortDetails(HashImpl.build(record.getHash()), parents, record.getCommitTime(), root,
                                             record.getSubject(), record.getAuthorName(), record.getAuthorEmail());
      }
    });
  }

  @NotNull
  public static List<TimedVcsCommit> readAllHashes(@NotNull final Project project,
                                                   @NotNull VirtualFile root,
                                                   @NotNull final Consumer<VcsUser> userRegistry) throws VcsException {
    final int COMMIT_BUFFER = 1000;

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    final GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, COMMIT_TIME,
                                                 AUTHOR_NAME, AUTHOR_EMAIL);
    h.setStdoutSuppressed(true);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters("HEAD", "--branches", "--remotes", "--tags");
    h.addParameters("--full-history", "--sparse");
    h.addParameters("--date-order");
    h.endOptions();

    final List<TimedVcsCommit> commits = ContainerUtil.newArrayList();

    final StringBuilder record = new StringBuilder();
    final AtomicInteger records = new AtomicInteger();
    final Ref<VcsException> ex = new Ref<VcsException>();
    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        try {
          int recordEnd = line.indexOf(GitLogParser.RECORD_END);
          String afterParseRemainder;
          if (recordEnd == line.length() - 1) { // ends with
            record.append(line);
            afterParseRemainder = "";
          }
          else if (recordEnd == -1) { // record doesn't end on this line => just appending, no parsing
            record.append(line);
            afterParseRemainder = null;
          }
          else { // record ends in the middle of this line
            record.append(line.substring(0, recordEnd + 1));
            afterParseRemainder = line.substring(recordEnd + 1);
          }
          if (afterParseRemainder != null && records.incrementAndGet() > COMMIT_BUFFER) { // null means can't parse now
            commits.addAll(parseCommit(project, parser, record, userRegistry));
            record.setLength(0);
            record.append(afterParseRemainder);
          }
        }
        catch (Exception e) {
          ex.set(new VcsException(e));
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        try {
          commits.addAll(parseCommit(project, parser, record, userRegistry));
        }
        catch (Exception e) {
          ex.set(new VcsException(e));
        }
      }

      @Override
      public void startFailed(Throwable exception) {
        ex.set(new VcsException(exception));
      }
    });
    h.runInCurrentThread(null);
    if (!ex.isNull()) {
      throw ex.get();
    }
    return commits;
  }

  private static List<TimedVcsCommit> parseCommit(final Project project, GitLogParser parser, StringBuilder record,
                                                  final Consumer<VcsUser> userRegistry) {
    List<GitLogRecord> rec = parser.parse(record.toString());
    return ContainerUtil.mapNotNull(rec, new Function<GitLogRecord, TimedVcsCommit>() {
      @Override
      public TimedVcsCommit fun(GitLogRecord record) {
        if (record == null) {
          return null;
        }
        TimedVcsCommit commit = convert(project, record);
        userRegistry.consume(vcsObjectsFactory(project).createUser(record.getAuthorName(), record.getAuthorEmail()));
        return commit;
      }
    });
  }

  @NotNull
  private static TimedVcsCommit convert(Project project, GitLogRecord rec) {
    VcsLogObjectsFactory factory = vcsObjectsFactory(project);
    List<Hash> parents = ContainerUtil.map(rec.getParentsHashes(), new Function<String, Hash>() {
      @Override
      public Hash fun(String s) {
        return HashImpl.build(s);
      }
    });
    return factory.createTimedCommit(HashImpl.build(rec.getHash()), parents, rec.getCommitTime());
  }

  @NotNull
  private static VcsLogObjectsFactory vcsObjectsFactory(@NotNull Project project) {
    return ServiceManager.getService(project, VcsLogObjectsFactory.class);
  }

  private static class MyTokenAccumulator {
    private final StringBuilder myBuffer = new StringBuilder();

    private boolean myNotStarted = true;
    private GitLogParser myParser;

    public MyTokenAccumulator(GitLogParser parser) {
      myParser = parser;
    }

    @Nullable
    public GitLogRecord acceptLine(String s) {
      final boolean lineEnd = s.startsWith(GitLogParser.RECORD_START);
      if (lineEnd && (!myNotStarted)) {
        final String line = myBuffer.toString();
        myBuffer.setLength(0);
        myBuffer.append(s.substring(GitLogParser.RECORD_START.length()));

        return processResult(line);
      }
      else {
        myBuffer.append(lineEnd ? s.substring(GitLogParser.RECORD_START.length()) : s);
        myBuffer.append("\n");
      }
      myNotStarted = false;

      return null;
    }

    public GitLogRecord processLast() {
      return processResult(myBuffer.toString());
    }

    private GitLogRecord processResult(final String line) {
      return myParser.parseOneRecord(line);
    }

  }

  /**
   * Get history for the file
   *
   * @param project the context project
   * @param path    the file path
   * @return the list of the revisions
   * @throws VcsException if there is problem with running git
   */
  public static List<VcsFileRevision> history(final Project project, final FilePath path) throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    return history(project, path, root);
  }

  /**
   * Get history for the file
   *
   * @param project the context project
   * @param path    the file path
   * @return the list of the revisions
   * @throws VcsException if there is problem with running git
   */
  public static List<VcsFileRevision> history(final Project project, FilePath path, final VirtualFile root, final String... parameters) throws VcsException {
    final List<VcsFileRevision> rc = new ArrayList<VcsFileRevision>();
    final List<VcsException> exceptions = new ArrayList<VcsException>();

    history(project, path, root, new Consumer<GitFileRevision>() {
      @Override public void consume(GitFileRevision gitFileRevision) {
        rc.add(gitFileRevision);
      }
    }, new Consumer<VcsException>() {
      @Override public void consume(VcsException e) {
        exceptions.add(e);
      }
    }, parameters);
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
    return rc;
  }

  public static List<Pair<SHAHash, Date>> onlyHashesHistory(Project project, FilePath path, final String... parameters)
    throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    return onlyHashesHistory(project, path, root, parameters);
  }

  public static List<Pair<SHAHash, Date>> onlyHashesHistory(Project project, FilePath path, final VirtualFile root, final String... parameters)
    throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();

    final List<Pair<SHAHash, Date>> rc = new ArrayList<Pair<SHAHash, Date>>();
    for (GitLogRecord record : parser.parse(output)) {
      record.setUsedHandler(h);
      rc.add(new Pair<SHAHash, Date>(new SHAHash(record.getHash()), record.getDate()));
    }
    return rc;
  }

  /**
   * <p>Get & parse git log detailed output with commits, their parents and their changes.</p>
   *
   * <p>Warning: this is method is efficient by speed, but don't query too much, because the whole log output is retrieved at once,
   *    and it can occupy too much memory. The estimate is ~600Kb for 1000 commits.</p>
   */
  @NotNull
  public static List<GitCommit> history(@NotNull final Project project, @NotNull final VirtualFile root, String... parameters)
                                        throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME,
                                           AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL, PARENTS, SUBJECT, BODY, RAW_BODY);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters("--full-history", "--sparse");
    h.endOptions();

    String output = h.run();

    List<GitLogRecord> records = parser.parse(output);

    return ContainerUtil.mapNotNull(records, new Function<GitLogRecord, GitCommit>() {
      @Override
      public GitCommit fun(GitLogRecord record) {
        try {
          return createCommit(project, root, record);
        }
        catch (VcsException e) {
          LOG.error(e);
          return null;
        }
      }
    });
  }

  private static GitCommit createCommit(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLogRecord record)
                                        throws VcsException {
    List<Hash> parents = ContainerUtil.map(record.getParentsHashes(), new Function<String, Hash>() {
      @Override
      public Hash fun(String hash) {
        return HashImpl.build(hash);
      }
    });
    VcsLogObjectsFactory factory = vcsObjectsFactory(project);
    return new GitCommit(project, HashImpl.build(record.getHash()), parents, record.getCommitTime(), root, record.getSubject(),
                         factory.createUser(record.getAuthorName(), record.getAuthorEmail()), record.getFullMessage(),
                         factory.createUser(record.getCommitterName(), record.getCommitterEmail()), record.getAuthorTimeStamp(),
                         record.parseChanges(project, root));
  }

  /**
   * <p>Returns the history queried by {@code git log}} command with a possibility to asynchronously process each log record
   *    returned by Git.</p>
   * <p>This method is not efficient.
   *    Consider using {@link #history(Project, VirtualFile, String...)} if you can afford storing the whole Git output in memory while
   *    parsing.</p>
   */
  public static void historyWithLinks(final Project project,
                                      FilePath path,
                                      @Nullable final SymbolicRefsI refs,
                                      @NotNull final AsynchConsumer<GitHeavyCommit> gitCommitConsumer,
                                      @Nullable final Getter<Boolean> isCanceled,
                                      @Nullable Collection<VirtualFile> paths,
                                      boolean fullHistory, final String... parameters) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    final GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL,
                                                 COMMITTER_NAME, COMMITTER_EMAIL, PARENTS, REF_NAMES, SUBJECT, BODY, RAW_BODY);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8");
    if (fullHistory) {
      h.addParameters("--full-history");
    }
    if (paths != null && ! paths.isEmpty()) {
      h.endOptions();
      h.addRelativeFiles(paths);
    } else {
      if (fullHistory) {
        h.addParameters("--sparse");
      }
      h.endOptions();
      h.addRelativePaths(path);
    }

    final VcsException[] exc = new VcsException[1];
    final Semaphore semaphore = new Semaphore();
    final StringBuilder sb = new StringBuilder();
    final Ref<Boolean> skipFirst = new Ref<Boolean>(true);
    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(final String line, final Key outputType) {
        try {
          if (ProcessOutputTypes.STDOUT.equals(outputType)) {
            if (isCanceled != null && isCanceled.get()) {
              h.cancel();
              return;
            }
            //if (line.charAt(line.length() - 1) != '\u0003') {
            if ((! line.startsWith("\u0001")) || skipFirst.get()) {
              if (sb.length() > 0) {
                sb.append("\n");
              }
              sb.append(line);
              skipFirst.set(false);
              return;
            }
            takeLine(project, line, sb, parser, refs, root, exc, h, gitCommitConsumer);
          }
        } catch (ProcessCanceledException e) {
          h.cancel();
          semaphore.up();
        }
      }
      @Override
      public void processTerminated(int exitCode) {
        semaphore.up();
      }
      @Override
      public void startFailed(Throwable exception) {
        semaphore.up();
      }
    });
    semaphore.down();
    h.start();
    semaphore.waitFor();
    takeLine(project, "", sb, parser, refs, root, exc, h, gitCommitConsumer);
    gitCommitConsumer.finished();
    if (exc[0] != null) {
      throw exc[0];
    }
  }

  private static void takeLine(final Project project, String line,
                               StringBuilder sb,
                               GitLogParser parser,
                               SymbolicRefsI refs,
                               VirtualFile root,
                               VcsException[] exc, GitLineHandler h, AsynchConsumer<GitHeavyCommit> gitCommitConsumer) {
    final String text = sb.toString();
    sb.setLength(0);
    sb.append(line);
    if (text.length() == 0) return;
    GitLogRecord record = parser.parseOneRecord(text);

    final GitHeavyCommit gitCommit;
    try {
      gitCommit = createCommit(project, refs, root, record);
    }
    catch (VcsException e) {
      exc[0] = e;
      h.cancel();
      return;
    }
    gitCommitConsumer.consume(gitCommit);
  }

  @NotNull
  private static GitHeavyCommit createCommit(@NotNull Project project, @Nullable SymbolicRefsI refs, @NotNull VirtualFile root,
                                        @NotNull GitLogRecord record) throws VcsException {
    final Collection<String> currentRefs = record.getRefs();
    List<String> locals = new ArrayList<String>();
    List<String> remotes = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();
    final String s = parseRefs(refs, currentRefs, locals, remotes, tags);

    GitHeavyCommit
      gitCommit = new GitHeavyCommit(root, AbstractHash.create(record.getHash()), new SHAHash(record.getHash()), record.getAuthorName(),
                                      record.getCommitterName(),
                                      record.getDate(), record.getSubject(), record.getFullMessage(),
                                      new HashSet<String>(Arrays.asList(record.getParentsHashes())), record.getFilePaths(root),
                                      record.getAuthorEmail(),
                                      record.getCommitterEmail(), tags, locals, remotes,
                                      record.parseChanges(project, root), record.getAuthorTimeStamp());
    gitCommit.setCurrentBranch(s);
    return gitCommit;
  }

  @Nullable
  private static String parseRefs(@Nullable SymbolicRefsI refs, Collection<String> currentRefs, List<String> locals,
                                List<String> remotes, List<String> tags) {
    if (refs == null) {
      return null;
    }
    for (String ref : currentRefs) {
      final SymbolicRefs.Kind kind = refs.getKind(ref);
      if (SymbolicRefs.Kind.LOCAL.equals(kind)) {
        locals.add(ref);
      } else if (SymbolicRefs.Kind.REMOTE.equals(kind)) {
        remotes.add(ref);
      } else {
        tags.add(ref);
      }
    }
    if (refs.getCurrent() != null && currentRefs.contains(refs.getCurrent().getName())) {
      return refs.getCurrent().getName();
    }
    return null;
  }

  @Nullable
  public static Pair<AbstractHash, AbstractHash> getStashTop(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.STASH.readLockingCommand());
    GitLogParser parser = new GitLogParser(project, HASH, PARENTS);
    h.setSilent(true);
    h.addParameters("list");
    h.addParameters("-n1");
    h.addParameters(parser.getPretty());

    String out;
    h.setCharset(Charset.forName(GitConfigUtil.getLogEncoding(project, root)));
    out = h.run();
    final List<GitLogRecord> gitLogRecords = parser.parse(out);
    for (GitLogRecord gitLogRecord : gitLogRecords) {
      ProgressManager.checkCanceled();

      GitSimpleHandler h1 = new GitSimpleHandler(project, root, GitCommand.LOG);
      GitLogParser parser1 = new GitLogParser(project, HASH, PARENTS, SUBJECT);
      h1.setSilent(true);
      h1.addParameters("-n1");
      h1.addParameters(parser1.getPretty());
      //h1.endOptions();
      h1.addParameters(gitLogRecord.getHash());

      String out1;
      out1 = h1.run();
      final List<GitLogRecord> gitLogRecords1 = parser1.parse(out1);
      LOG.assertTrue(gitLogRecords1.size() == 1, String.format("gitLogRecords size is incorrect. size: %s, records: %s, output: %s",
                                                               gitLogRecords1.size(), gitLogRecords1, out1));
      final GitLogRecord logRecord = gitLogRecords1.get(0);
      final String[] parentsShortHashes = logRecord.getParentsHashes();
      String indexCommit = null;
      // heuristics
      if (parentsShortHashes.length == 2) {
        if (logRecord.getSubject().contains(parentsShortHashes[0])) {
          indexCommit = parentsShortHashes[1];
        }
        if (logRecord.getSubject().contains(parentsShortHashes[1])) {
          indexCommit = parentsShortHashes[0];
        }
      }
      return new Pair<AbstractHash, AbstractHash>(AbstractHash.create(gitLogRecord.getHash()), indexCommit == null ? null : AbstractHash.create(indexCommit));
    }
    return null;
  }

  @Nullable
  public static List<Pair<String, GitHeavyCommit>> loadStashStackAsCommits(@NotNull Project project, @NotNull VirtualFile root,
                                                                      SymbolicRefsI refs, final String... parameters) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.STASH.readLockingCommand());
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
                                           COMMITTER_EMAIL, PARENTS, REF_NAMES, SHORT_REF_LOG_SELECTOR, SUBJECT, BODY, RAW_BODY);
    h.setSilent(true);
    h.addParameters("list");
    h.addParameters(parameters);
    h.addParameters(parser.getPretty());

    String out;
    h.setCharset(Charset.forName(GitConfigUtil.getLogEncoding(project, root)));
    out = h.run();
    final List<GitLogRecord> gitLogRecords = parser.parse(out);
    final List<Pair<String, GitHeavyCommit>> result = new ArrayList<Pair<String, GitHeavyCommit>>();
    for (GitLogRecord gitLogRecord : gitLogRecords) {
      ProgressManager.checkCanceled();
      final GitHeavyCommit gitCommit = createCommit(project, refs, root, gitLogRecord);
      result.add(new Pair<String, GitHeavyCommit>(gitLogRecord.getShortenedRefLog(), gitCommit));
    }
    return result;
  }

  @Deprecated
  @NotNull
  public static List<GitHeavyCommit> commitsDetails(@NotNull Project project, @NotNull FilePath path, @Nullable SymbolicRefsI refs,
                                               @NotNull final Collection<String> commitsIds) throws VcsException {
    path = getLastCommitName(project, path);     // adjust path using change manager
    VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS,
                                           HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
                                           COMMITTER_EMAIL, PARENTS, REF_NAMES, SUBJECT, BODY, RAW_BODY);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-status", "-M", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(new ArrayList<String>(commitsIds));

    String output = h.run();
    final List<GitHeavyCommit> rc = new ArrayList<GitHeavyCommit>();
    for (GitLogRecord record : parser.parse(output)) {
      final GitHeavyCommit gitCommit = createCommit(project, refs, root, record);
      rc.add(gitCommit);
    }
    return rc;
  }

  @NotNull
  public static List<GitCommit> commitsDetails(@NotNull Project project, @NotNull VirtualFile root,
                                               @NotNull final Collection<String> hashes) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS,
                                           HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
                                           COMMITTER_EMAIL, PARENTS, REF_NAMES, SUBJECT, BODY, RAW_BODY);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-status", "-M", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(new ArrayList<String>(hashes));

    String output = h.run();
    final List<GitCommit> rc = new ArrayList<GitCommit>();
    for (GitLogRecord record : parser.parse(output)) {
      rc.add(createCommit(project, root, record));
    }
    return rc;
  }

  @NotNull
  public static List<GitCommit> getAllDetails(@NotNull Project project, @NotNull VirtualFile root,
                                              @NotNull List<String> parameters) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS,
                                           HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
                                           COMMITTER_EMAIL, PARENTS, REF_NAMES, SUBJECT, BODY, RAW_BODY);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-status", "-M", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(parameters);

    String output = h.run();
    final List<GitCommit> rc = new ArrayList<GitCommit>();
    for (GitLogRecord record : parser.parse(output)) {
      rc.add(createCommit(project, root, record));
    }
    return rc;
  }

  public static long getAuthorTime(Project project, FilePath path, final String commitsId) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, AUTHOR_TIME);
    h.setSilent(true);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(commitsId);

    String output = h.run();
    GitLogRecord logRecord = parser.parseOneRecord(output);
    return logRecord.getAuthorTimeStamp();
  }

  public static void hashesWithParents(Project project, FilePath path, final AsynchConsumer<CommitHashPlusParents> consumer,
                                       final Getter<Boolean> isCanceled,
                                       Collection<VirtualFile> paths, final String... parameters) throws VcsException {
    // adjust path using change manager
    path = getLastCommitName(project, path);
    final VirtualFile root = GitUtil.getGitRoot(path);
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    final GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NAME, HASH, COMMIT_TIME, PARENTS, AUTHOR_NAME);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8", "--full-history");

    if (paths != null && ! paths.isEmpty()) {
      h.endOptions();
      h.addRelativeFiles(paths);
    } else {
      h.addParameters("--sparse");
      h.endOptions();
      h.addRelativePaths(path);
    }

    final Semaphore semaphore = new Semaphore();
    h.addLineListener(new GitLineHandlerListener() {
      @Override
      public void onLineAvailable(final String line, final Key outputType) {
        try {
          if (ProcessOutputTypes.STDOUT.equals(outputType)) {
            if (isCanceled != null && isCanceled.get()) {
              h.cancel();
              return;
            }
            GitLogRecord record = parser.parseOneRecord(line);
            consumer.consume(new CommitHashPlusParents(record.getHash(),
                                                       record.getParentsHashes(), record.getCommitTime(),
                                                       record.getAuthorName()));
          }
        } catch (ProcessCanceledException e) {
          h.cancel();
          semaphore.up();
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        semaphore.up();
      }

      @Override
      public void startFailed(Throwable exception) {
        semaphore.up();
      }
    });
    semaphore.down();
    h.start();
    semaphore.waitFor();
    consumer.finished();
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
  public static GitRevisionNumber getMergeBase(final Project project, final VirtualFile root, @NotNull final String first,
                                               @NotNull final String second)
    throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.MERGE_BASE);
    h.setSilent(true);
    h.addParameters(first, second);
    String output = h.run().trim();
    if (output.length() == 0) {
      return null;
    }
    else {
      return GitRevisionNumber.resolve(project, root, output);
    }
  }
}
