// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.util.StopWatch;
import git4idea.GitCommit;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.*;
import git4idea.config.GitVersionSpecialty;
import git4idea.log.GitLogProvider;
import git4idea.log.GitRefManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static git4idea.history.GitLogParser.GitLogOption.*;

public class GitLogUtil {
  private static final Logger LOG = Logger.getInstance(GitLogUtil.class);
  public static final String GRAFTED = "grafted";
  public static final String REPLACED = "replaced";
  /**
   * A parameter to {@code git log} which is equivalent to {@code --all}, but doesn't show the stuff from index or stash.
   */
  public static final List<String> LOG_ALL = ContainerUtil.immutableList(GitUtil.HEAD, "--branches", "--remotes", "--tags");
  public static final String STDIN = "--stdin";

  @NotNull
  public static List<? extends VcsCommitMetadata> collectMetadata(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile root,
                                                                  @NotNull List<String> hashes)
    throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    GitLineHandler h = createGitHandler(project, root);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, AUTHOR_NAME, AUTHOR_EMAIL,
                                           COMMIT_TIME, SUBJECT, COMMITTER_NAME, COMMITTER_EMAIL, AUTHOR_TIME, BODY, RAW_BODY);
    h.setSilent(true);
    // git show can show either -p, or --name-status, or --name-only, but we need nothing, just details => using git log --no-walk
    h.addParameters(getNoWalkParameter(vcs));
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(STDIN);
    h.endOptions();

    sendHashesToStdin(vcs, hashes, h);

    String output = Git.getInstance().runCommand(h).getOutputOrThrow();
    List<GitLogRecord> records = parser.parse(output);

    return ContainerUtil.map(records, record -> {
      List<Hash> parents = new SmartList<>();
      for (String parent : record.getParentsHashes()) {
        parents.add(HashImpl.build(parent));
      }
      record.setUsedHandler(h);
      return factory.createCommitMetadata(HashImpl.build(record.getHash()), parents, record.getCommitTime(), root,
                                          record.getSubject(), record.getAuthorName(), record.getAuthorEmail(), record.getFullMessage(),
                                          record.getCommitterName(), record.getCommitterEmail(), record.getAuthorTimeStamp());
    });
  }

  public static void readTimedCommits(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull List<String> parameters,
                                      @Nullable Consumer<? super VcsUser> userConsumer,
                                      @Nullable Consumer<? super VcsRef> refConsumer,
                                      @NotNull Consumer<? super TimedVcsCommit> commitConsumer) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    GitLineHandler handler = createGitHandler(project, root, Collections.emptyList(), false);
    List<GitLogParser.GitLogOption> options = ContainerUtil.newArrayList(HASH, PARENTS, COMMIT_TIME);
    if (userConsumer != null) {
      options.add(AUTHOR_NAME);
      options.add(AUTHOR_EMAIL);
    }
    if (refConsumer != null) {
      options.add(REF_NAMES);
    }

    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, options.toArray(new GitLogParser.GitLogOption[0]));
    handler.setStdoutSuppressed(true);
    handler.addParameters(parser.getPretty(), "--encoding=UTF-8");
    handler.addParameters("--decorate=full");
    handler.addParameters(parameters);
    handler.endOptions();

    GitLogOutputSplitter handlerListener = new GitLogOutputSplitter(handler, parser, record -> {
      Hash hash = HashImpl.build(record.getHash());
      List<Hash> parents = getParentHashes(factory, record);
      commitConsumer.consume(factory.createTimedCommit(hash, parents, record.getCommitTime()));

      if (refConsumer != null) {
        for (VcsRef ref : parseRefs(record.getRefs(), hash, factory, root)) {
          refConsumer.consume(ref);
        }
      }

      if (userConsumer != null) userConsumer.consume(factory.createUser(record.getAuthorName(), record.getAuthorEmail()));
    });
    Git.getInstance().runCommandWithoutCollectingOutput(handler);
    handlerListener.reportErrors();
  }

  @NotNull
  private static Collection<VcsRef> parseRefs(@NotNull Collection<String> refs,
                                              @NotNull Hash hash,
                                              @NotNull VcsLogObjectsFactory factory,
                                              @NotNull VirtualFile root) {
    return ContainerUtil.mapNotNull(refs, refName -> {
      if (refName.equals(GRAFTED) || refName.equals(REPLACED)) return null;
      VcsRefType type = GitRefManager.getRefType(refName);
      refName = GitBranchUtil.stripRefsPrefix(refName);
      return refName.equals(GitUtil.ORIGIN_HEAD) ? null : factory.createRef(hash, refName, type, root);
    });
  }

  @NotNull
  public static VcsLogProvider.DetailedLogData collectMetadata(@NotNull Project project,
                                                               @NotNull VirtualFile root,
                                                               boolean lowPriorityProcess,
                                                               String... params) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return LogDataImpl.empty();
    }
    Set<VcsRef> refs = new OpenTHashSet<>(GitLogProvider.DONT_CONSIDER_SHA);
    List<VcsCommitMetadata> commits =
      collectMetadata(project, root, lowPriorityProcess, record -> {
        GitCommit commit = createCommit(project, root, Collections.singletonList(record), factory);
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

  @NotNull
  private static List<VcsCommitMetadata> collectMetadata(@NotNull Project project,
                                                         @NotNull VirtualFile root,
                                                         boolean lowPriorityProcess,
                                                         @NotNull NullableFunction<GitLogRecord, VcsCommitMetadata> converter,
                                                         String... parameters) throws VcsException {

    List<VcsCommitMetadata> commits = ContainerUtil.newArrayList();

    try {
      GitLineHandler handler = createGitHandler(project, root, Collections.emptyList(), lowPriorityProcess);
      readRecordsFromHandler(project, root, true, false, false, false, record -> commits.add(converter.fun(record)), handler, parameters);
    }
    catch (VcsException e) {
      if (commits.isEmpty()) {
        throw e;
      }
      LOG.warn("Error during loading details, returning partially loaded commits\n", e);
    }

    return commits;
  }

  @Nullable
  public static VcsLogObjectsFactory getObjectsFactoryWithDisposeCheck(@NotNull Project project) {
    return ReadAction.compute(() -> {
      if (!project.isDisposed()) {
        return ServiceManager.getService(project, VcsLogObjectsFactory.class);
      }
      return null;
    });
  }

  @NotNull
  private static GitCommit createCommit(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<GitLogRecord> records,
                                        @NotNull VcsLogObjectsFactory factory) {
    return createCommit(project, root, records, factory, DiffRenameLimit.GIT_CONFIG);
  }

  @NotNull
  private static GitCommit createCommit(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<GitLogRecord> records,
                                        @NotNull VcsLogObjectsFactory factory, @NotNull DiffRenameLimit renameLimit) {
    GitLogRecord record = notNull(ContainerUtil.getLastItem(records));
    List<Hash> parents = getParentHashes(factory, record);

    return new GitCommit(project, HashImpl.build(record.getHash()), parents, record.getCommitTime(), root, record.getSubject(),
                         factory.createUser(record.getAuthorName(), record.getAuthorEmail()), record.getFullMessage(),
                         factory.createUser(record.getCommitterName(), record.getCommitterEmail()), record.getAuthorTimeStamp(),
                         ContainerUtil.map(records, GitLogRecord::getStatusInfos), renameLimit);
  }

  @NotNull
  public static List<Hash> getParentHashes(@NotNull VcsLogObjectsFactory factory, @NotNull GitLogRecord record) {
    return ContainerUtil.map(record.getParentsHashes(), factory::createHash);
  }

  /**
   * @deprecated use {@link GitHistoryUtils#history(Project, VirtualFile, String...)} instead.
   */
  @Deprecated
  @NotNull
  public static List<GitCommit> collectFullDetails(@NotNull Project project,
                                                   @NotNull VirtualFile root,
                                                   String... parameters) throws VcsException {

    List<GitCommit> commits = ContainerUtil.newArrayList();
    try {
      readFullDetails(project, root, commits::add, true, true, false, parameters);
    }
    catch (VcsException e) {
      if (commits.isEmpty()) {
        throw e;
      }
      LOG.warn("Error during loading details, returning partially loaded commits\n", e);
    }
    return commits;
  }

  public static void readFullDetails(@NotNull Project project,
                                     @NotNull VirtualFile root,
                                     @NotNull Consumer<? super GitCommit> commitConsumer,
                                     boolean includeRootChanges,
                                     boolean preserveOrder,
                                     boolean lowPriorityProcess,
                                     @NotNull String... parameters) throws VcsException {
    readFullDetails(project, root, commitConsumer, includeRootChanges, preserveOrder, lowPriorityProcess, true, true, parameters);
  }

  public static void readFullDetails(@NotNull Project project,
                                     @NotNull VirtualFile root,
                                     @NotNull Consumer<? super GitCommit> commitConsumer,
                                     boolean includeRootChanges,
                                     boolean preserveOrder,
                                     boolean lowPriorityProcess,
                                     boolean withRenames,
                                     boolean withFullMergeDiff,
                                     @NotNull String... parameters) throws VcsException {
    DiffRenameLimit renameLimit = withRenames ? DiffRenameLimit.REGISTRY : DiffRenameLimit.NO_RENAMES;
    GitLineHandler handler = createGitHandler(project, root, createConfigParameters(includeRootChanges, renameLimit), lowPriorityProcess);
    readFullDetailsFromHandler(project, root, commitConsumer, renameLimit, handler, preserveOrder, withFullMergeDiff,
                               parameters);
  }

  private static void readFullDetailsFromHandler(@NotNull Project project,
                                                 @NotNull VirtualFile root,
                                                 @NotNull Consumer<? super GitCommit> commitConsumer,
                                                 @NotNull DiffRenameLimit renameLimit,
                                                 @NotNull GitLineHandler handler,
                                                 boolean preserveOrder,
                                                 boolean withFullMergeDiff,
                                                 @NotNull String... parameters) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }
    boolean withRenames = renameLimit != DiffRenameLimit.NO_RENAMES;

    if (withFullMergeDiff) {
      Consumer<List<GitLogRecord>> consumer = records -> {
        GitLogRecord firstRecord = notNull(getFirstItem(records));
        String[] parents = firstRecord.getParentsHashes();

        LOG.assertTrue(parents.length == 0 || parents.length == records.size(), "Not enough records for commit " +
                                                                                firstRecord.getHash() +
                                                                                " expected " +
                                                                                parents.length +
                                                                                " records, but got " +
                                                                                records.size());

        commitConsumer.consume(createCommit(project, root, records, factory, renameLimit));
      };
      GitLogRecordCollector recordCollector = preserveOrder ? new GitLogRecordCollector(project, root, consumer)
                                                            : new GitLogUnorderedRecordCollector(project, root, consumer);

      readRecordsFromHandler(project, root, false, true, withRenames, true, recordCollector, handler, parameters);
      recordCollector.finish();
    }
    else {
      Consumer<GitLogRecord> consumer =
        record -> commitConsumer.consume(createCommit(project, root, ContainerUtil.newArrayList(record), factory, renameLimit));

      readRecordsFromHandler(project, root, false, true, withRenames, false, consumer, handler, parameters);
    }
  }

  private static void readRecordsFromHandler(@NotNull Project project,
                                             @NotNull VirtualFile root,
                                             boolean withRefs,
                                             boolean withChanges,
                                             boolean withRenames,
                                             boolean withFullMergeDiff,
                                             @NotNull Consumer<GitLogRecord> converter,
                                             @NotNull GitLineHandler handler,
                                             @NotNull String... parameters)
    throws VcsException {
    GitLogParser.GitLogOption[] options = {HASH, COMMIT_TIME, AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL, COMMITTER_NAME, COMMITTER_EMAIL,
      PARENTS, SUBJECT, BODY, RAW_BODY};
    if (withRefs) {
      options = ArrayUtil.append(options, REF_NAMES);
    }
    GitLogParser parser = new GitLogParser(project, withChanges ? GitLogParser.NameStatus.STATUS : GitLogParser.NameStatus.NONE, options);
    handler.setStdoutSuppressed(true);
    handler.addParameters(parameters);
    handler.addParameters(parser.getPretty(), "--encoding=UTF-8");
    if (withRefs) {
      handler.addParameters("--decorate=full");
    }
    if (withChanges) {
      handler.addParameters("--name-status");
    }
    if (withRenames) {
      handler.addParameters("-M");
    }
    if (withFullMergeDiff) {
      handler.addParameters("-m");
    }
    handler.endOptions();

    StopWatch sw = StopWatch.start("loading details in [" + root.getName() + "]");

    GitLogOutputSplitter handlerListener = new GitLogOutputSplitter(handler, parser, converter);
    Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError();
    handlerListener.reportErrors();

    sw.report();
  }

  public static void readFullDetailsForHashes(@NotNull Project project,
                                              @NotNull VirtualFile root,
                                              @NotNull GitVcs vcs,
                                              @NotNull Consumer<? super GitCommit> commitConsumer,
                                              @NotNull List<String> hashes,
                                              boolean includeRootChanges,
                                              boolean lowPriorityProcess,
                                              @NotNull DiffRenameLimit renameLimit) throws VcsException {
    readFullDetailsForHashes(project, root, vcs, commitConsumer, hashes, includeRootChanges, lowPriorityProcess, true, false, renameLimit);
  }

  public static void readFullDetailsForHashes(@NotNull Project project,
                                              @NotNull VirtualFile root,
                                              @NotNull GitVcs vcs,
                                              @NotNull Consumer<? super GitCommit> commitConsumer,
                                              @NotNull List<String> hashes,
                                              boolean includeRootChanges,
                                              boolean lowPriorityProcess,
                                              boolean withFullMergeDiff,
                                              boolean preserveOrder,
                                              @NotNull DiffRenameLimit renameLimit) throws VcsException {
    if (hashes.isEmpty()) return;
    GitLineHandler handler = createGitHandler(project, root, createConfigParameters(includeRootChanges, renameLimit), lowPriorityProcess);
    sendHashesToStdin(vcs, hashes, handler);

    readFullDetailsFromHandler(project, root, commitConsumer, renameLimit, handler, preserveOrder, withFullMergeDiff,
                               getNoWalkParameter(vcs), STDIN);
  }

  public static void sendHashesToStdin(@NotNull GitVcs vcs, @NotNull Collection<String> hashes, @NotNull GitHandler handler) {
    // if we close this stream, RunnerMediator won't be able to send ctrl+c to the process in order to softly kill it
    // see RunnerMediator.sendCtrlEventThroughStream
    String separator = GitVersionSpecialty.LF_SEPARATORS_IN_STDIN.existsIn(vcs.getVersion()) ? "\n" : System.lineSeparator();
    handler.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(hashes,
                                                                      separator,
                                                                      handler.getCharset(),
                                                                      true));
  }

  @NotNull
  public static String getNoWalkParameter(@NotNull GitVcs vcs) {
    return GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(vcs.getVersion()) ? "--no-walk=unsorted" : "--no-walk";
  }

  @NotNull
  public static GitLineHandler createGitHandler(@NotNull Project project, @NotNull VirtualFile root) {
    return createGitHandler(project, root, Collections.emptyList(), false);
  }

  @NotNull
  private static GitLineHandler createGitHandler(@NotNull Project project,
                                                 @NotNull VirtualFile root,
                                                 @NotNull List<String> configParameters,
                                                 boolean lowPriorityProcess) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LOG, configParameters);
    if (lowPriorityProcess) handler.withLowPriority();
    handler.setWithMediator(false);
    return handler;
  }

  @NotNull
  private static List<String> createConfigParameters(boolean includeRootChanges, @NotNull DiffRenameLimit renameLimit) {
    List<String> result = ContainerUtil.newArrayList();
    switch (renameLimit) {
      case INFINITY:
        result.add(renameLimit(0));
        break;
      case REGISTRY:
        result.add(renameLimit(Registry.intValue("git.diff.renameLimit")));
        break;
      case NO_RENAMES:
        result.add("diff.renames=false");
        break;
      case GIT_CONFIG:
    }

    if (!includeRootChanges) {
      result.add("log.showRoot=false");
    }
    return result;
  }

  @NotNull
  private static String renameLimit(int limit) {
    return "diff.renameLimit=" + limit;
  }

  public enum DiffRenameLimit {
    /**
     * Use zero value
     */
    INFINITY,
    /**
     * Use value set in registry (usually 1000)
     */
    REGISTRY,
    /**
     * Use value set in users git.config
     */
    GIT_CONFIG,
    /**
     * Disable renames detection
     */
    NO_RENAMES
  }
}
