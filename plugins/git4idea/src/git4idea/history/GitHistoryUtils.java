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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.util.StopWatch;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.*;
import git4idea.config.GitVersion;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.history.browser.SHAHash;
import git4idea.history.browser.SymbolicRefs;
import git4idea.history.browser.SymbolicRefsI;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.i18n.GitBundle;
import git4idea.log.GitLogProvider;
import git4idea.log.GitRefManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.util.ObjectUtils.notNull;
import static git4idea.history.GitLogParser.GitLogOption.*;

/**
 * A collection of methods for retrieving history information from native Git.
 */
public class GitHistoryUtils {

  /**
   * A parameter to {@code git log} which is equivalent to {@code --all}, but doesn't show the stuff from index or stash.
   */
  public static final List<String> LOG_ALL = Arrays.asList("HEAD", "--branches", "--remotes", "--tags");

  private static final Logger LOG = Logger.getInstance("#git4idea.history.GitHistoryUtils");

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
  public static VcsRevisionDescription getCurrentRevisionDescription(@NotNull Project project, @NotNull FilePath filePath)
    throws VcsException {
    filePath = getLastCommitName(project, filePath);
    GitSimpleHandler h = new GitSimpleHandler(project, GitUtil.getGitRoot(filePath), GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME, AUTHOR_NAME, COMMITTER_NAME, SUBJECT, BODY, RAW_BODY);
    h.setSilent(true);
    h.addParameters("-n1", parser.getPretty());
    h.addParameters("--all");
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
  public static ItemLatestState getLastRevision(@NotNull Project project, @NotNull FilePath filePath) throws VcsException {
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
   *
   * @param project
   * @param path              FilePath which history is queried.
   * @param root              Git root - optional: if this is null, then git root will be detected automatically.
   * @param consumer          This consumer is notified ({@link Consumer#consume(Object)} when new history records are retrieved.
   * @param exceptionConsumer This consumer is notified in case of error while executing git command.
   * @param parameters        Optional parameters which will be added to the git log command just before the path.
   */
  public static void history(@NotNull Project project,
                             @NotNull FilePath path,
                             @Nullable VirtualFile root,
                             @NotNull Consumer<GitFileRevision> consumer,
                             @NotNull Consumer<VcsException> exceptionConsumer,
                             String... parameters) {
    history(project, path, root, GitRevisionNumber.HEAD, consumer, exceptionConsumer, parameters);
  }

  public static void history(@NotNull Project project,
                             @NotNull FilePath path,
                             @Nullable VirtualFile root,
                             @NotNull VcsRevisionNumber startingRevision,
                             @NotNull Consumer<GitFileRevision> consumer,
                             @NotNull Consumer<VcsException> exceptionConsumer,
                             String... parameters) {
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

    final AtomicReference<String> firstCommit = new AtomicReference<>(startingRevision.asString());
    final AtomicReference<String> firstCommitParent = new AtomicReference<>(firstCommit.get());
    final AtomicReference<FilePath> currentPath = new AtomicReference<>(filePath);
    final AtomicReference<GitLineHandler> logHandler = new AtomicReference<>();
    final AtomicBoolean skipFurtherOutput = new AtomicBoolean();

    final Consumer<GitLogRecord> resultAdapter = record -> {
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
      if (parentHashes.length < 1) {
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

        Couple<String> authorPair = Couple.of(record.getAuthorName(), record.getAuthorEmail());
        Couple<String> committerPair = Couple.of(record.getCommitterName(), record.getCommitterEmail());
        Collection<String> parents = Arrays.asList(parentHashes);
        consumer.consume(new GitFileRevision(project, finalRoot, revisionPath, revision, Couple.of(authorPair, committerPair), message,
                                             null, new Date(record.getAuthorTimeStamp()), parents));
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
    };

    GitVcs vcs = GitVcs.getInstance(project);
    GitVersion version = vcs != null ? vcs.getVersion() : GitVersion.NULL;
    final AtomicBoolean criticalFailure = new AtomicBoolean();
    while (currentPath.get() != null && firstCommitParent.get() != null) {
      logHandler.set(getLogHandler(project, version, finalRoot, logParser, currentPath.get(), firstCommitParent.get(), parameters));
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
          }
          finally {
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
        Pair<String, FilePath> firstCommitParentAndPath = getFirstCommitParentAndPathIfRename(project, finalRoot, firstCommit.get(),
                                                                                              currentPath.get(), version);
        currentPath.set(firstCommitParentAndPath == null ? null : firstCommitParentAndPath.second);
        firstCommitParent.set(firstCommitParentAndPath == null ? null : firstCommitParentAndPath.first);
        skipFurtherOutput.set(false);
      }
      catch (VcsException e) {
        LOG.warn("Tried to get first commit rename path", e);
        exceptionConsumer.consume(e);
        return;
      }
    }
  }

  @NotNull
  private static GitLineHandler getLogHandler(@NotNull Project project,
                                              @NotNull GitVersion version,
                                              @NotNull VirtualFile root,
                                              @NotNull GitLogParser parser,
                                              @NotNull FilePath path,
                                              @NotNull String lastCommit,
                                              String... parameters) {
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    h.setStdoutSuppressed(true);
    h.addParameters("--name-status", parser.getPretty(), "--encoding=UTF-8", lastCommit);
    if (GitVersionSpecialty.FULL_HISTORY_SIMPLIFY_MERGES_WORKS_CORRECTLY.existsIn(version) && Registry.is("git.file.history.full")) {
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
   * Gets info of the given commit and checks if it was a RENAME.
   * If yes, returns the older file path, which file was renamed from.
   * If it's not a rename, returns null.
   */
  @Nullable
  private static Pair<String, FilePath> getFirstCommitParentAndPathIfRename(@NotNull Project project,
                                                                            @NotNull VirtualFile root,
                                                                            @NotNull String commit,
                                                                            @NotNull FilePath filePath,
                                                                            @NotNull GitVersion version) throws VcsException {
    // 'git show -M --name-status <commit hash>' returns the information about commit and detects renames.
    // NB: we can't specify the filepath, because then rename detection will work only with the '--follow' option, which we don't wanna use.
    final GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    final GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, HASH, COMMIT_TIME, PARENTS);
    h.setStdoutSuppressed(true);
    h.addParameters("-M", "--name-status", parser.getPretty(), "--encoding=UTF-8", commit);
    if (!GitVersionSpecialty.FOLLOW_IS_BUGGY_IN_THE_LOG.existsIn(version)) {
      h.addParameters("--follow");
      h.endOptions();
      h.addRelativePaths(filePath);
    }
    else {
      h.endOptions();
    }
    final String output = h.run();
    final List<GitLogRecord> records = parser.parse(output);

    if (records.isEmpty()) return null;
    // we have information about all changed files of the commit. Extracting information about the file we need.
    GitLogRecord record = records.get(0);
    final List<Change> changes = record.parseChanges(project, root);
    for (Change change : changes) {
      if ((change.isMoved() || change.isRenamed()) && filePath.equals(notNull(change.getAfterRevision()).getFile())) {
        final String[] parents = record.getParentsHashes();
        String parent = parents.length > 0 ? parents[0] : null;
        return Pair.create(parent, notNull(change.getBeforeRevision()).getFile());
      }
    }
    return null;
  }

  @NotNull
  public static List<? extends VcsShortCommitDetails> readMiniDetails(@NotNull Project project,
                                                                      @NotNull VirtualFile root,
                                                                      @NotNull List<String> hashes)
    throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, AUTHOR_NAME,
                                           AUTHOR_EMAIL, COMMIT_TIME, SUBJECT, COMMITTER_NAME, COMMITTER_EMAIL, AUTHOR_TIME);
    h.setSilent(true);
    // git show can show either -p, or --name-status, or --name-only, but we need nothing, just details => using git log --no-walk
    h.addParameters("--no-walk");
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(new ArrayList<>(hashes));
    h.endOptions();

    String output = h.run();
    List<GitLogRecord> records = parser.parse(output);

    return ContainerUtil.map(records, record -> {
      List<Hash> parents = new SmartList<>();
      for (String parent : record.getParentsHashes()) {
        parents.add(HashImpl.build(parent));
      }
      return factory.createShortDetails(HashImpl.build(record.getHash()), parents, record.getCommitTime(), root,
                                        record.getSubject(), record.getAuthorName(), record.getAuthorEmail(), record.getCommitterName(),
                                        record.getCommitterEmail(),
                                        record.getAuthorTimeStamp());
    });
  }

  @Nullable
  public static List<VcsCommitMetadata> readLastCommits(@NotNull Project project,
                                                        @NotNull VirtualFile root,
                                                        @NotNull String... refs)
    throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return null;
    }

    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, COMMIT_TIME, SUBJECT, AUTHOR_NAME,
                                           AUTHOR_EMAIL, RAW_BODY, COMMITTER_NAME, COMMITTER_EMAIL, AUTHOR_TIME);

    h.setSilent(true);
    // git show can show either -p, or --name-status, or --name-only, but we need nothing, just details => using git log --no-walk
    h.addParameters("--no-walk");
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(refs);
    h.endOptions();

    String output = h.run();
    List<GitLogRecord> records = parser.parse(output);
    if (records.size() != refs.length) return null;

    return ContainerUtil.map(records,
                             record -> factory.createCommitMetadata(factory.createHash(record.getHash()), getParentHashes(factory, record),
                                                                    record.getCommitTime(),
                                                                    root, record.getSubject(), record.getAuthorName(),
                                                                    record.getAuthorEmail(),
                                                                    record.getFullMessage(), record.getCommitterName(),
                                                                    record.getCommitterEmail(),
                                                                    record.getAuthorTimeStamp()));
  }

  private static void processHandlerOutputByLine(@NotNull GitLineHandler handler,
                                                 @NotNull GitLogParser parser,
                                                 @NotNull Consumer<GitLogRecord> recordConsumer) throws VcsException {
    Ref<Throwable> parseError = new Ref<>();
    processHandlerOutputByLine(handler, builder -> {
      try {
        GitLogRecord record = parser.parseOneRecord(builder.toString());
        if (record != null) {
          recordConsumer.consume(record);
        }
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Throwable t) {
        if (parseError.isNull()) {
          parseError.set(t);
          LOG.error("Could not parse \" " + StringUtil.escapeStringCharacters(builder.toString()) + "\"\n" +
                    "Command " + handler.printableCommandLine(), t);
        }
      }
    }, 0);

    if (!parseError.isNull()) {
      throw new VcsException(parseError.get());
    }
  }

  private static void processHandlerOutputByLine(@NotNull GitLineHandler handler,
                                                 @NotNull Consumer<StringBuilder> recordConsumer,
                                                 int bufferSize)
    throws VcsException {
    MyGitLineHandlerListener handlerListener = new MyGitLineHandlerListener(handler, recordConsumer, bufferSize);
    handler.runInCurrentThread(null);
    handlerListener.reportErrors();
  }

  public static void readCommits(@NotNull Project project,
                                 @NotNull VirtualFile root,
                                 @NotNull List<String> parameters,
                                 @NotNull Consumer<VcsUser> userConsumer,
                                 @NotNull Consumer<VcsRef> refConsumer,
                                 @NotNull Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    final GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, COMMIT_TIME,
                                                 AUTHOR_NAME, AUTHOR_EMAIL, REF_NAMES);
    h.setStdoutSuppressed(true);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters("--decorate=full");
    h.addParameters(parameters);
    h.endOptions();

    final int COMMIT_BUFFER = 1000;
    processHandlerOutputByLine(h, buffer -> {
      List<TimedVcsCommit> commits = parseCommit(parser, buffer, userConsumer, refConsumer, factory, root);
      for (TimedVcsCommit commit : commits) {
        commitConsumer.consume(commit);
      }
    }, COMMIT_BUFFER);
  }

  @NotNull
  private static List<TimedVcsCommit> parseCommit(@NotNull GitLogParser parser,
                                                  @NotNull StringBuilder record,
                                                  @NotNull Consumer<VcsUser> userRegistry,
                                                  @NotNull Consumer<VcsRef> refConsumer,
                                                  @NotNull VcsLogObjectsFactory factory,
                                                  @NotNull VirtualFile root) {
    List<GitLogRecord> gitLogRecords = parser.parse(record.toString());
    return ContainerUtil.mapNotNull(gitLogRecords, gitLogRecord -> {
      if (gitLogRecord == null) {
        return null;
      }
      Pair<TimedVcsCommit, Collection<VcsRef>> pair = convert(gitLogRecord, factory, root);
      TimedVcsCommit commit = pair.first;
      for (VcsRef ref : pair.second) {
        refConsumer.consume(ref);
      }
      userRegistry.consume(factory.createUser(gitLogRecord.getAuthorName(), gitLogRecord.getAuthorEmail()));
      return commit;
    });
  }

  @NotNull
  private static Pair<TimedVcsCommit, Collection<VcsRef>> convert(@NotNull GitLogRecord rec,
                                                                  @NotNull VcsLogObjectsFactory factory,
                                                                  @NotNull VirtualFile root) {
    Hash hash = HashImpl.build(rec.getHash());
    List<Hash> parents = getParentHashes(factory, rec);
    TimedVcsCommit commit = factory.createTimedCommit(hash, parents, rec.getCommitTime());
    return Pair.create(commit, parseRefs(rec.getRefs(), hash, factory, root));
  }

  @NotNull
  private static Collection<VcsRef> parseRefs(@NotNull Collection<String> refs,
                                              @NotNull Hash hash,
                                              @NotNull VcsLogObjectsFactory factory,
                                              @NotNull VirtualFile root) {
    return ContainerUtil.mapNotNull(refs, refName -> {
      VcsRefType type = GitRefManager.getRefType(refName);
      refName = GitBranchUtil.stripRefsPrefix(refName);
      return refName.equals(GitUtil.ORIGIN_HEAD) ? null : factory.createRef(hash, refName, type, root);
    });
  }

  @Nullable
  private static VcsLogObjectsFactory getObjectsFactoryWithDisposeCheck(@NotNull Project project) {
    return ReadAction.compute(() -> {
      if (!project.isDisposed()) {
        return ServiceManager.getService(project, VcsLogObjectsFactory.class);
      }
      return null;
    });
  }

  private static class MyTokenAccumulator {
    @NotNull private final StringBuilder myBuffer = new StringBuilder();
    @NotNull private final GitLogParser myParser;

    private boolean myNotStarted = true;

    public MyTokenAccumulator(@NotNull GitLogParser parser) {
      myParser = parser;
    }

    @Nullable
    public GitLogRecord acceptLine(String s) {
      final boolean recordStart = s.startsWith(GitLogParser.RECORD_START);
      if (recordStart) {
        s = s.substring(GitLogParser.RECORD_START.length());
      }

      if (myNotStarted) {
        myBuffer.append(s);
        myBuffer.append("\n");

        myNotStarted = false;
        return null;
      }
      else if (recordStart) {
        final String line = myBuffer.toString();
        myBuffer.setLength(0);

        myBuffer.append(s);
        myBuffer.append("\n");

        return processResult(line);
      }
      else {
        myBuffer.append(s);
        myBuffer.append("\n");
        return null;
      }
    }

    @Nullable
    public GitLogRecord processLast() {
      return processResult(myBuffer.toString());
    }

    @Nullable
    private GitLogRecord processResult(@NotNull String line) {
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
  @NotNull
  public static List<VcsFileRevision> history(@NotNull Project project, @NotNull FilePath path, String... parameters) throws VcsException {
    final VirtualFile root = GitUtil.getGitRoot(path);
    return history(project, path, root, parameters);
  }

  @NotNull
  public static List<VcsFileRevision> history(@NotNull Project project,
                                              @NotNull FilePath path,
                                              @Nullable VirtualFile root,
                                              String... parameters) throws VcsException {
    return history(project, path, root, GitRevisionNumber.HEAD, parameters);
  }

  @NotNull
  public static List<VcsFileRevision> history(@NotNull Project project,
                                              @NotNull FilePath path,
                                              @Nullable VirtualFile root,
                                              @NotNull VcsRevisionNumber startingFrom,
                                              String... parameters) throws VcsException {
    final List<VcsFileRevision> rc = new ArrayList<>();
    final List<VcsException> exceptions = new ArrayList<>();

    history(project, path, root, startingFrom, rc::add, exceptions::add, parameters);
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
    return rc;
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
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, HASH, COMMIT_TIME);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.endOptions();
    h.addRelativePaths(path);
    String output = h.run();

    final List<Pair<SHAHash, Date>> rc = new ArrayList<>();
    for (GitLogRecord record : parser.parse(output)) {
      record.setUsedHandler(h);
      rc.add(Pair.create(new SHAHash(record.getHash()), record.getDate()));
    }
    return rc;
  }

  @NotNull
  public static VcsLogProvider.DetailedLogData loadMetadata(@NotNull final Project project,
                                                            @NotNull final VirtualFile root,
                                                            String... params) throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return LogDataImpl.empty();
    }
    final Set<VcsRef> refs = new OpenTHashSet<>(GitLogProvider.DONT_CONSIDER_SHA);
    final List<VcsCommitMetadata> commits =
      collectDetails(project, root, true, false, record -> {
        GitCommit commit = createCommit(project, root, record, factory);
        Collection<VcsRef> refsInRecord = parseRefs(record.getRefs(), commit.getId(), factory, root);
        for (VcsRef ref : refsInRecord) {
          if (!refs.add(ref)) {
            VcsRef otherRef = ContainerUtil.find(refs, r -> GitLogProvider.DONT_CONSIDER_SHA.equals(r, ref));
            LOG.error("Adding duplicate element " + ref + " to the set containing " + otherRef);
          }
        }
        return commit;
      }, params);
    return new LogDataImpl(refs, commits);
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
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }
    return collectDetails(project, root, false, true, record -> createCommit(project, root, record, factory), parameters);
  }

  @NotNull
  private static GitLogParser createParserForDetails(@NotNull GitTextHandler h,
                                                     @NotNull Project project,
                                                     boolean withRefs,
                                                     boolean withChanges,
                                                     String... parameters) {
    GitLogParser.NameStatus status = withChanges ? GitLogParser.NameStatus.STATUS : GitLogParser.NameStatus.NONE;
    GitLogParser.GitLogOption[] options = {HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL,
      PARENTS, SUBJECT, BODY, RAW_BODY};
    if (withRefs) {
      options = ArrayUtil.append(options, REF_NAMES);
    }
    GitLogParser parser = new GitLogParser(project, status, options);
    h.setStdoutSuppressed(true);
    h.addParameters(parameters);
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    if (withRefs) {
      h.addParameters("--decorate=full");
    }
    if (withChanges) {
      h.addParameters("-M", /*find and report renames*/
                      "--name-status",
                      "-c" /*single diff for merge commits, only showing files that were modified from both parents*/);
    }
    h.endOptions();

    return parser;
  }

  @NotNull
  public static <T> List<T> collectDetails(@NotNull Project project,
                                           @NotNull VirtualFile root,
                                           boolean withRefs,
                                           boolean withChanges,
                                           @NotNull NullableFunction<GitLogRecord, T> converter,
                                           String... parameters) throws VcsException {

    List<T> commits = ContainerUtil.newArrayList();

    loadDetails(project, root, withRefs, withChanges, record -> commits.add(converter.fun(record)), parameters);

    return commits;
  }

  public static void loadDetails(@NotNull Project project,
                                 @NotNull VirtualFile root,
                                 @NotNull Consumer<VcsFullCommitDetails> commitConsumer,
                                 @NotNull String... parameters) throws VcsException {
    final VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    loadDetails(project, root, false, true, record -> commitConsumer.consume(createCommit(project, root, record, factory)), parameters);
  }

  public static void loadDetails(@NotNull Project project,
                                 @NotNull VirtualFile root,
                                 boolean withRefs,
                                 boolean withChanges,
                                 @NotNull Consumer<GitLogRecord> converter,
                                 String... parameters)
    throws VcsException {

    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser parser = createParserForDetails(h, project, withRefs, withChanges, parameters);

    StopWatch sw = StopWatch.start("loading details");

    processHandlerOutputByLine(h, parser, record -> converter.consume(record));

    sw.report();
  }

  @NotNull
  private static GitCommit createCommit(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLogRecord record,
                                        @NotNull VcsLogObjectsFactory factory) {
    List<Hash> parents = getParentHashes(factory, record);
    return new GitCommit(project, HashImpl.build(record.getHash()), parents, record.getCommitTime(), root, record.getSubject(),
                         factory.createUser(record.getAuthorName(), record.getAuthorEmail()), record.getFullMessage(),
                         factory.createUser(record.getCommitterName(), record.getCommitterEmail()), record.getAuthorTimeStamp(),
                         record.getStatusInfos());
  }

  @NotNull
  private static List<Hash> getParentHashes(@NotNull VcsLogObjectsFactory factory, @NotNull GitLogRecord record) {
    return ContainerUtil.map(record.getParentsHashes(), factory::createHash);
  }

  @NotNull
  private static GitHeavyCommit createCommit(@NotNull Project project, @Nullable SymbolicRefsI refs, @NotNull VirtualFile root,
                                             @NotNull GitLogRecord record) throws VcsException {
    final Collection<String> currentRefs = record.getRefs();
    List<String> locals = new ArrayList<>();
    List<String> remotes = new ArrayList<>();
    List<String> tags = new ArrayList<>();
    final String s = parseRefs(refs, currentRefs, locals, remotes, tags);

    GitHeavyCommit
      gitCommit = new GitHeavyCommit(root, AbstractHash.create(record.getHash()), new SHAHash(record.getHash()), record.getAuthorName(),
                                     record.getCommitterName(),
                                     record.getDate(), record.getSubject(), record.getFullMessage(),
                                     new HashSet<>(Arrays.asList(record.getParentsHashes())), record.getFilePaths(root),
                                     record.getAuthorEmail(),
                                     record.getCommitterEmail(), tags, locals, remotes,
                                     record.parseChanges(project, root), record.getAuthorTimeStamp());
    gitCommit.setCurrentBranch(s);
    return gitCommit;
  }

  @Nullable
  private static String parseRefs(@Nullable SymbolicRefsI refs, @NotNull Collection<String> currentRefs, @NotNull List<String> locals,
                                  @NotNull List<String> remotes, @NotNull List<String> tags) {
    if (refs == null) {
      return null;
    }
    for (String ref : currentRefs) {
      final SymbolicRefs.Kind kind = refs.getKind(ref);
      if (SymbolicRefs.Kind.LOCAL.equals(kind)) {
        locals.add(ref);
      }
      else if (SymbolicRefs.Kind.REMOTE.equals(kind)) {
        remotes.add(ref);
      }
      else {
        tags.add(ref);
      }
    }
    if (refs.getCurrent() != null && currentRefs.contains(refs.getCurrent().getName())) {
      return refs.getCurrent().getName();
    }
    return null;
  }

  @Deprecated
  @NotNull
  public static List<GitHeavyCommit> commitsDetails(@NotNull Project project, @NotNull FilePath path, @Nullable SymbolicRefsI refs,
                                                    @NotNull Collection<String> commitsIds) throws VcsException {
    path = getLastCommitName(project, path);     // adjust path using change manager
    VirtualFile root = GitUtil.getGitRoot(path);
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.SHOW);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS,
                                           HASH, HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME,
                                           COMMITTER_EMAIL, PARENTS, REF_NAMES, SUBJECT, BODY, RAW_BODY);
    h.setSilent(true);
    h.addParameters("--name-status", "-M", parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(new ArrayList<>(commitsIds));

    String output = h.run();
    final List<GitHeavyCommit> rc = new ArrayList<>();
    for (GitLogRecord record : parser.parse(output)) {
      final GitHeavyCommit gitCommit = createCommit(project, refs, root, record);
      rc.add(gitCommit);
    }
    return rc;
  }

  public static long getAuthorTime(@NotNull Project project, @NotNull FilePath path, @NotNull String commitsId) throws VcsException {
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

  private static class MyGitLineHandlerListener implements GitLineHandlerListener {
    @NotNull private final GitLineHandler myHandler;
    @NotNull private final Consumer<StringBuilder> myRecordConsumer;
    private final int myBufferSize;

    @NotNull private final StringBuilder myOutput = new StringBuilder();
    @NotNull private final StringBuilder myErrors = new StringBuilder();
    @Nullable private VcsException myException = null;

    private int myRecords = 0;
    private boolean myIsInsideBody = true;

    public MyGitLineHandlerListener(@NotNull GitLineHandler handler,
                                    @NotNull Consumer<StringBuilder> recordConsumer,
                                    int bufferSize) {
      myHandler = handler;
      myRecordConsumer = recordConsumer;
      myBufferSize = bufferSize;

      myHandler.addLineListener(this);
    }

    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (outputType == ProcessOutputTypes.STDERR) {
        myErrors.append(line).append("\n");
      }
      else if (outputType == ProcessOutputTypes.STDOUT) {
        try {
          processOutputLine(line);
        }
        catch (Exception e) {
          myException = new VcsException(e);
        }
      }
    }

    private void processOutputLine(@NotNull String line) {
      // format of the record is <RECORD_START><BODY><RECORD_END><CHANGES>
      // then next record goes
      // (rather inconveniently, after RECORD_END there is a list of modified files)
      // so here I'm trying to find text between two RECORD_START symbols
      // that simultaneously contains a RECORD_END
      // this helps to deal with commits like a929478f6720ac15d949117188cd6798b4a9c286 in linux repo that have RECORD_START symbols in the message
      // wont help with RECORD_END symbols in the message however (have not seen those yet)

      if (myIsInsideBody) {
        // find body
        int bodyEnd = line.indexOf(GitLogParser.RECORD_END);
        if (bodyEnd >= 0) {
          myIsInsideBody = false;
          myOutput.append(line.substring(0, bodyEnd + 1));
          processOutputLine(line.substring(bodyEnd + 1));
        }
        else {
          myOutput.append(line).append("\n");
        }
      }
      else {
        int nextRecordStart = line.indexOf(GitLogParser.RECORD_START);
        if (nextRecordStart >= 0) {
          myOutput.append(line.substring(0, nextRecordStart));
          if (++myRecords > myBufferSize) {
            myRecordConsumer.consume(myOutput);
            myOutput.setLength(0);
          }
          myIsInsideBody = true;
          processOutputLine(line.substring(nextRecordStart));
        }
        else {
          myOutput.append(line).append("\n");
        }
      }
    }

    @Override
    public void processTerminated(int exitCode) {
      if (exitCode != 0) {
        String errorMessage = myErrors.toString();
        if (errorMessage.isEmpty()) {
          errorMessage = GitBundle.message("git.error.exit", exitCode);
        }
        myException = new VcsException(errorMessage + "\nCommand line: [" + myHandler.printableCommandLine() + "]");
      }
      else {
        try {
          myRecordConsumer.consume(myOutput);
        }
        catch (Exception e) {
          myException = new VcsException(e);
        }
      }
    }

    @Override
    public void startFailed(Throwable exception) {
      myException = new VcsException(exception);
    }

    public void reportErrors() throws VcsException {
      if (myException != null) {
        if (myException.getCause() instanceof ProcessCanceledException) {
          throw (ProcessCanceledException)myException.getCause();
        }
        throw myException;
      }
    }
  }
}
