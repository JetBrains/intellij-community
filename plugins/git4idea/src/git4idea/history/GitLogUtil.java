/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStreamWriter;
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
    h.addParameters("--no-walk");
    h.addParameters(parser.getPretty(), "--encoding=UTF-8");
    h.addParameters(new ArrayList<>(hashes));
    h.endOptions();

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

    GitLineHandler handler = createGitHandler(project, root);
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
                                                               String... params) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return LogDataImpl.empty();
    }
    Set<VcsRef> refs = new OpenTHashSet<>(GitLogProvider.DONT_CONSIDER_SHA);
    List<VcsCommitMetadata> commits =
      collectMetadata(project, root, record -> {
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
                                                         @NotNull NullableFunction<GitLogRecord, VcsCommitMetadata> converter,
                                                         String... parameters) throws VcsException {

    List<VcsCommitMetadata> commits = ContainerUtil.newArrayList();

    try {
      readRecords(project, root, true, false, DiffRenameLimit.GIT_CONFIG, record -> commits.add(converter.fun(record)), parameters);
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
      readFullDetails(project, root, commits::add, parameters);
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
                                     @NotNull String... parameters) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    GitLogRecordCollector recordCollector = new GitLogRecordCollector(project, root) {
      @Override
      public void consume(@NotNull List<GitLogRecord> records) {
        assertCorrectNumberOfRecords(records);
        commitConsumer.consume(createCommit(project, root, records, factory));
      }
    };
    readRecords(project, root, false, true, DiffRenameLimit.REGISTRY, recordCollector, parameters);
    recordCollector.finish();
  }

  public static void assertCorrectNumberOfRecords(@NotNull List<GitLogRecord> records) {
    GitLogRecord firstRecord = notNull(getFirstItem(records));
    String[] parents = firstRecord.getParentsHashes();
    LOG.assertTrue(parents.length == 0 || parents.length == records.size(), "Not enough records for commit " +
                                                                            firstRecord.getHash() +
                                                                            " expected " +
                                                                            parents.length +
                                                                            " records, but got " +
                                                                            records.size());
  }

  private static void readRecords(@NotNull Project project,
                                  @NotNull VirtualFile root,
                                  boolean withRefs,
                                  boolean withChanges,
                                  @NotNull DiffRenameLimit renameLimit,
                                  @NotNull Consumer<GitLogRecord> converter,
                                  String... parameters) throws VcsException {
    GitLineHandler handler = createGitHandler(project, root, createConfigParameters(withChanges, renameLimit));
    readRecordsFromHandler(project, root, withRefs, withChanges, converter, handler, parameters);
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
    Git.getInstance().runCommandWithoutCollectingOutput(handler);
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
                                              @NotNull DiffRenameLimit renameLimit) throws VcsException {
    VcsLogObjectsFactory factory = getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    GitLogRecordCollector recordCollector = new GitLogRecordCollector(project, root) {
      @Override
      public void consume(@NotNull List<GitLogRecord> records) {
        assertCorrectNumberOfRecords(records);
        commitConsumer.consume(createCommit(project, root, records, factory, renameLimit));
      }
    };
    GitLineHandler handler = createGitHandler(project, root, createConfigParameters(true, renameLimit));
    sendHashesToStdin(vcs, hashes, handler);

    readRecordsFromHandler(project, root, false, true, recordCollector, handler, getNoWalkParameter(vcs), STDIN);
    recordCollector.finish();

    if (!handler.errors().isEmpty()) {
      throw new VcsException(GitUIUtil.stringifyErrors(handler.errors()));
    }
  }

  public static void sendHashesToStdin(@NotNull GitVcs vcs, @NotNull Collection<String> hashes, @NotNull GitHandler handler) {
    String separator = getSeparator(vcs);
    handler.setInputProcessor(stream -> {
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      OutputStreamWriter writer = new OutputStreamWriter(stream, handler.getCharset());
      // if we close this stream, RunnerMediator won't be able to send ctrl+c to the process in order to softly kill it
      // see RunnerMediator.sendCtrlEventThroughStream
      for (String hash : hashes) {
        writer.write(hash);
        writer.write(separator);
      }
      writer.write(separator);
      writer.flush();
    });
  }

  @NotNull
  private static String getSeparator(@NotNull GitVcs vcs) {
    if (GitVersionSpecialty.LF_SEPARATORS_IN_STDIN.existsIn(vcs.getVersion())) {
      return "\n";
    }
    return System.lineSeparator();
  }

  @NotNull
  public static String getNoWalkParameter(@NotNull GitVcs vcs) {
    return GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(vcs.getVersion()) ? "--no-walk=unsorted" : "--no-walk";
  }

  @NotNull
  private static GitLineHandler createGitHandler(@NotNull Project project, @NotNull VirtualFile root) {
    return createGitHandler(project, root, Collections.emptyList());
  }

  @NotNull
  private static GitLineHandler createGitHandler(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<String> configParameters) {
    return new GitLineHandler(project, root, GitCommand.LOG, configParameters, false);
  }

  @NotNull
  private static List<String> createConfigParameters(boolean withChanges, @NotNull DiffRenameLimit renameLimit) {
    if (!withChanges) return Collections.emptyList();
    switch (renameLimit) {
      case INFINITY:
        return renameLimit(0);
      case REGISTRY:
        return renameLimit(Registry.intValue("git.diff.renameLimit"));
      case GIT_CONFIG:
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<String> renameLimit(int limit) {
    return Collections.singletonList("diff.renameLimit=" + limit);
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
