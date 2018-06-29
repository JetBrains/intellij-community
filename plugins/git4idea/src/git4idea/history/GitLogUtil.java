// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import java.util.*;

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
  public static final List<String> LOG_ALL = Arrays.asList("HEAD", "--branches", "--remotes", "--tags");
  public static final String STDIN = "--stdin";

  @NotNull
  public static List<? extends VcsShortCommitDetails> collectShortDetails(@NotNull Project project,
                                                                          @NotNull GitVcs vcs,
                                                                          @NotNull VirtualFile root,
                                                                          @NotNull List<String> hashes)
    throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    GitLineHandler h = createGitHandler(project, root);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, AUTHOR_NAME,
                                           AUTHOR_EMAIL, COMMIT_TIME, SUBJECT, COMMITTER_NAME, COMMITTER_EMAIL, AUTHOR_TIME);
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
      return factory.createShortDetails(HashImpl.build(record.getHash()), parents, record.getCommitTime(), root,
                                        record.getSubject(), record.getAuthorName(), record.getAuthorEmail(), record.getCommitterName(),
                                        record.getCommitterEmail(),
                                        record.getAuthorTimeStamp());
    });
  }

  public static void readTimedCommits(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull List<String> parameters,
                                      @NotNull Consumer<VcsUser> userConsumer,
                                      @NotNull Consumer<VcsRef> refConsumer,
                                      @NotNull Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    GitLineHandler handler = createGitHandler(project, root, Collections.emptyList(), false);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, PARENTS, COMMIT_TIME,
                                           AUTHOR_NAME, AUTHOR_EMAIL, REF_NAMES);
    handler.setStdoutSuppressed(true);
    handler.addParameters(parser.getPretty(), "--encoding=UTF-8");
    handler.addParameters("--decorate=full");
    handler.addParameters(parameters);
    handler.endOptions();

    GitLogOutputSplitter handlerListener = new GitLogOutputSplitter(handler, parser, record -> {
      Hash hash = HashImpl.build(record.getHash());
      List<Hash> parents = getParentHashes(factory, record);
      commitConsumer.consume(factory.createTimedCommit(hash, parents, record.getCommitTime()));

      for (VcsRef ref : parseRefs(record.getRefs(), hash, factory, root)) {
        refConsumer.consume(ref);
      }

      userConsumer.consume(factory.createUser(record.getAuthorName(), record.getAuthorEmail()));
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
      GitLineHandler handler = createGitHandler(project, root, createConfigParameters(false, false, DiffRenameLimit.GIT_CONFIG), lowPriorityProcess);
      readRecordsFromHandler(project, root, true, false, record -> commits.add(converter.fun(record)), handler, parameters);
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
                                     boolean preserverOrder,
                                     boolean lowPriorityProcess,
                                     @NotNull String... parameters) throws VcsException {
    DiffRenameLimit renameLimit = DiffRenameLimit.REGISTRY;

    GitLineHandler handler = createGitHandler(project, root, createConfigParameters(true, includeRootChanges, renameLimit), lowPriorityProcess);
    readFullDetailsFromHandler(project, root, commitConsumer, renameLimit, handler, preserverOrder, parameters);
  }

  private static void readFullDetailsFromHandler(@NotNull Project project,
                                                 @NotNull VirtualFile root,
                                                 @NotNull Consumer<? super GitCommit> commitConsumer,
                                                 @NotNull DiffRenameLimit renameLimit,
                                                 @NotNull GitLineHandler handler,
                                                 boolean preserverOrder,
                                                 @NotNull String... parameters) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

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

    GitLogRecordCollector recordCollector = preserverOrder ? new GitLogRecordCollector(project, root, consumer)
                                                           : new GitLogUnorderedRecordCollector(project, root, consumer);

    readRecordsFromHandler(project, root, false, true, recordCollector, handler, parameters);
    recordCollector.finish();
  }

  private static void readRecordsFromHandler(@NotNull Project project,
                                             @NotNull VirtualFile root,
                                             boolean withRefs,
                                             boolean withChanges,
                                             @NotNull Consumer<GitLogRecord> converter,
                                             @NotNull GitLineHandler handler,
                                             @NotNull String... parameters)
    throws VcsException {
    GitLogParser parser = createParserForDetails(handler, project, withRefs, withChanges, parameters);

    StopWatch sw = StopWatch.start("loading details in [" + root.getName() + "]");

    GitLogOutputSplitter handlerListener = new GitLogOutputSplitter(handler, parser, converter);
    Git.getInstance().runCommandWithoutCollectingOutput(handler).getOutputOrThrow();
    handlerListener.reportErrors();

    sw.report();
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
                      "-m" /*merge commits show diff with all parents (ie for merge with 3 parents we are going to have 3 separate entries, one for each parent)*/);
    }
    h.endOptions();

    return parser;
  }

  public static void readFullDetailsForHashes(@NotNull Project project,
                                              @NotNull VirtualFile root,
                                              @NotNull GitVcs vcs,
                                              @NotNull Consumer<? super GitCommit> commitConsumer,
                                              @NotNull List<String> hashes,
                                              boolean includeRootChanges,
                                              boolean lowPriorityProcess,
                                              @NotNull DiffRenameLimit renameLimit) throws VcsException {
    GitLineHandler handler =
      createGitHandler(project, root, createConfigParameters(true, includeRootChanges, renameLimit), lowPriorityProcess);
    sendHashesToStdin(vcs, hashes, handler);

    readFullDetailsFromHandler(project, root, commitConsumer, renameLimit, handler, false, getNoWalkParameter(vcs), STDIN);
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
    handler.setWithLowPriority(lowPriorityProcess);
    handler.setWithMediator(false);
    return handler;
  }

  @NotNull
  private static List<String> createConfigParameters(boolean withChanges,
                                                     boolean includeRootChanges,
                                                     @NotNull DiffRenameLimit renameLimit) {
    if (!withChanges) return Collections.emptyList();

    List<String> result = ContainerUtil.newArrayList();
    switch (renameLimit) {
      case INFINITY:
        result.add(renameLimit(0));
        break;
      case REGISTRY:
        result.add(renameLimit(Registry.intValue("git.diff.renameLimit")));
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
    GIT_CONFIG
  }
}
