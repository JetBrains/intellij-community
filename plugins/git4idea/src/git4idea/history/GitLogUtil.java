// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.history.GitLogParser.GitLogOption.*;

@ApiStatus.Internal
public class GitLogUtil {
  private static final Logger LOG = Logger.getInstance(GitLogUtil.class);
  public static final String GRAFTED = "grafted";
  public static final String REPLACED = "replaced";
  /**
   * A parameter to {@code git log} which is equivalent to {@code --all}, but doesn't show the stuff from index or stash.
   */
  public static final List<String> LOG_ALL = ContainerUtil.immutableList(GitUtil.HEAD, "--branches", "--remotes", "--tags");
  public static final String STDIN = "--stdin";
  static final GitLogParser.GitLogOption[] COMMIT_METADATA_OPTIONS = {
    HASH, PARENTS,
    COMMIT_TIME, COMMITTER_NAME, COMMITTER_EMAIL,
    AUTHOR_NAME, AUTHOR_TIME, AUTHOR_EMAIL,
    SUBJECT, BODY, RAW_BODY
  };

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

    GitLogParser<GitLogRecord> parser = GitLogParser.createDefaultParser(project, options.toArray(new GitLogParser.GitLogOption[0]));
    handler.setStdoutSuppressed(true);
    handler.addParameters(parser.getPretty(), "--encoding=UTF-8");
    handler.addParameters("--decorate=full");
    handler.addParameters(parameters);
    handler.endOptions();

    GitLogOutputSplitter<GitLogRecord> handlerListener = new GitLogOutputSplitter<>(handler, parser, record -> {
      Hash hash = HashImpl.build(record.getHash());
      List<Hash> parents = ContainerUtil.map(record.getParentsHashes(), factory::createHash);
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
  public static List<? extends VcsCommitMetadata> collectMetadata(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile root,
                                                                  @NotNull List<String> hashes)
    throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return Collections.emptyList();
    }

    GitLineHandler h = createGitHandler(project, root);
    GitLogParser<GitLogRecord> parser = GitLogParser.createDefaultParser(project, COMMIT_METADATA_OPTIONS);
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

  @NotNull
  public static VcsLogProvider.DetailedLogData collectMetadata(@NotNull Project project,
                                                               @NotNull VirtualFile root,
                                                               String... params) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return LogDataImpl.empty();
    }

    Set<VcsRef> refs = new OpenTHashSet<>(GitLogProvider.DONT_CONSIDER_SHA);
    List<VcsCommitMetadata> commits = new ArrayList<>();
    Consumer<GitLogRecord> recordConsumer = record -> {
      VcsCommitMetadata commit = createMetadata(root, record, factory);
      commits.add(commit);
      Collection<VcsRef> refsInRecord = parseRefs(record.getRefs(), commit.getId(), factory, root);
      for (VcsRef ref : refsInRecord) {
        if (!refs.add(ref)) {
          VcsRef otherRef = ContainerUtil.find(refs, r -> GitLogProvider.DONT_CONSIDER_SHA.equals(r, ref));
          LOG.error("Adding duplicate element " + ref + " to the set containing " + otherRef);
        }
      }
    };

    try {
      GitLineHandler handler = createGitHandler(project, root, Collections.emptyList(), false);
      GitLogParser.GitLogOption[] options = ArrayUtil.append(COMMIT_METADATA_OPTIONS, REF_NAMES);
      GitLogParser<GitLogRecord> parser = GitLogParser.createDefaultParser(project, options);
      handler.setStdoutSuppressed(true);
      handler.addParameters(params);
      handler.addParameters(parser.getPretty(), "--encoding=UTF-8");
      handler.addParameters("--decorate=full");
      handler.endOptions();

      StopWatch sw = StopWatch.start("loading commit metadata in [" + root.getName() + "]");

      GitLogOutputSplitter<GitLogRecord> handlerListener = new GitLogOutputSplitter<>(handler, parser, recordConsumer);
      Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError();
      handlerListener.reportErrors();

      sw.report();
    }
    catch (VcsException e) {
      if (commits.isEmpty()) {
        throw e;
      }
      LOG.warn("Error during loading details, returning partially loaded commits\n", e);
    }

    return new LogDataImpl(refs, commits);
  }

  public static void readFullDetails(@NotNull Project project,
                                     @NotNull VirtualFile root,
                                     @NotNull Consumer<? super GitCommit> commitConsumer,
                                     String @NotNull ... parameters) throws VcsException {
    new GitFullDetailsCollector(project, root, new InternedGitLogRecordBuilder())
      .readFullDetails(commitConsumer, GitCommitRequirements.DEFAULT, false, parameters);
  }

  public static void readFullDetailsForHashes(@NotNull Project project,
                                              @NotNull VirtualFile root,
                                              @NotNull List<String> hashes,
                                              @NotNull GitCommitRequirements requirements,
                                              @NotNull Consumer<? super GitCommit> commitConsumer) throws VcsException {
    new GitFullDetailsCollector(project, root, new InternedGitLogRecordBuilder())
      .readFullDetailsForHashes(hashes, requirements, false, commitConsumer);
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
  static VcsCommitMetadata createMetadata(@NotNull VirtualFile root, @NotNull GitLogRecord record,
                                          @NotNull VcsLogObjectsFactory factory) {
    List<Hash> parents = ContainerUtil.map(record.getParentsHashes(), factory::createHash);
    return factory.createCommitMetadata(factory.createHash(record.getHash()), parents, record.getCommitTime(), root, record.getSubject(),
                                        record.getAuthorName(), record.getAuthorEmail(), record.getFullMessage(),
                                        record.getCommitterName(), record.getCommitterEmail(), record.getAuthorTimeStamp());
  }

  static void sendHashesToStdin(@NotNull GitVcs vcs, @NotNull Collection<String> hashes, @NotNull GitHandler handler) {
    // if we close this stream, RunnerMediator won't be able to send ctrl+c to the process in order to softly kill it
    // see RunnerMediator.sendCtrlEventThroughStream
    handler.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(hashes,
                                                                      "\n",
                                                                      handler.getCharset(),
                                                                      true));
  }

  @NotNull
  static String getNoWalkParameter(@NotNull GitVcs vcs) {
    return GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(vcs) ? "--no-walk=unsorted" : "--no-walk";
  }

  @NotNull
  static GitLineHandler createGitHandler(@NotNull Project project, @NotNull VirtualFile root) {
    return createGitHandler(project, root, Collections.emptyList(), false);
  }

  @NotNull
  static GitLineHandler createGitHandler(@NotNull Project project,
                                         @NotNull VirtualFile root,
                                         @NotNull List<String> configParameters,
                                         boolean lowPriorityProcess) {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LOG, configParameters);
    if (lowPriorityProcess) handler.withLowPriority();
    handler.setWithMediator(false);
    return handler;
  }
}
