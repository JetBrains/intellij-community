// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.StopWatch;
import git4idea.GitCommit;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

class GitDetailsCollector {
  private static final Logger LOG = Logger.getInstance(GitDetailsCollector.class);

  static void readFullDetails(@NotNull Project project,
                              @NotNull VirtualFile root,
                              @NotNull Consumer<? super GitCommit> commitConsumer,
                              @NotNull GitCommitRequirements requirements,
                              boolean lowPriorityProcess,
                              @NotNull String... parameters) throws VcsException {
    List<String> configParameters = requirements.configParameters();
    GitLineHandler handler = GitLogUtil.createGitHandler(project, root, configParameters, lowPriorityProcess);
    readFullDetailsFromHandler(project, root, commitConsumer, handler, requirements, parameters);
  }

  static void readFullDetailsForHashes(@NotNull Project project,
                                       @NotNull VirtualFile root,
                                       @NotNull GitVcs vcs,
                                       @NotNull List<String> hashes,
                                       @NotNull GitCommitRequirements requirements,
                                       boolean lowPriorityProcess,
                                       @NotNull Consumer<? super GitCommit> commitConsumer) throws VcsException {
    if (hashes.isEmpty()) return;
    GitLineHandler handler = GitLogUtil.createGitHandler(project, root, requirements.configParameters(), lowPriorityProcess);
    GitLogUtil.sendHashesToStdin(vcs, hashes, handler);

    readFullDetailsFromHandler(project, root, commitConsumer, handler, requirements, GitLogUtil.getNoWalkParameter(vcs), GitLogUtil.STDIN);
  }

  private static void readFullDetailsFromHandler(@NotNull Project project,
                                                 @NotNull VirtualFile root,
                                                 @NotNull Consumer<? super GitCommit> commitConsumer,
                                                 @NotNull GitLineHandler handler,
                                                 @NotNull GitCommitRequirements requirements,
                                                 @NotNull String... parameters) throws VcsException {
    VcsLogObjectsFactory factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project);
    if (factory == null) {
      return;
    }

    String[] commandParameters = ArrayUtil.mergeArrays(ArrayUtil.toStringArray(requirements.commandParameters()), parameters);
    if (requirements.getDiffInMergeCommits().equals(GitCommitRequirements.DiffInMergeCommits.DIFF_TO_PARENTS)) {
      Consumer<List<GitLogRecord>> consumer = records -> {
        GitLogRecord firstRecord = notNull(getFirstItem(records));
        String[] parents = firstRecord.getParentsHashes();

        LOG.assertTrue(parents.length == 0 || parents.length == records.size(), "Not enough records for commit " +
                                                                                firstRecord.getHash() +
                                                                                " expected " +
                                                                                parents.length +
                                                                                " records, but got " +
                                                                                records.size());

        commitConsumer.consume(createCommit(project, root, records, factory, requirements.getDiffRenameLimit()));
      };
      GitLogRecordCollector recordCollector = requirements.getPreserveOrder() ? new GitLogRecordCollector(project, root, consumer)
                                                                              : new GitLogUnorderedRecordCollector(project, root, consumer);

      readRecordsFromHandler(project, root, handler, recordCollector, commandParameters);
      recordCollector.finish();
    }
    else {
      Consumer<GitLogRecord> consumer = record -> commitConsumer.consume(createCommit(project, root,
                                                                                      ContainerUtil.newArrayList(record), factory,
                                                                                      requirements.getDiffRenameLimit()));

      readRecordsFromHandler(project, root, handler, consumer, commandParameters);
    }
  }

  private static void readRecordsFromHandler(@NotNull Project project,
                                             @NotNull VirtualFile root,
                                             @NotNull GitLineHandler handler,
                                             @NotNull Consumer<GitLogRecord> converter,
                                             @NotNull String... parameters)
    throws VcsException {
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.STATUS, GitLogUtil.COMMIT_METADATA_OPTIONS);
    handler.setStdoutSuppressed(true);
    handler.addParameters(parameters);
    handler.addParameters(parser.getPretty(), "--encoding=UTF-8");
    handler.addParameters("--name-status");
    handler.endOptions();

    StopWatch sw = StopWatch.start("loading details in [" + root.getName() + "]");

    GitLogOutputSplitter handlerListener = new GitLogOutputSplitter(handler, parser, converter);
    Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError();
    handlerListener.reportErrors();

    sw.report();
  }

  @NotNull
  private static GitCommit createCommit(@NotNull Project project, @NotNull VirtualFile root, @NotNull List<GitLogRecord> records,
                                        @NotNull VcsLogObjectsFactory factory, @NotNull GitCommitRequirements.DiffRenameLimit renameLimit) {
    GitLogRecord record = notNull(ContainerUtil.getLastItem(records));
    List<Hash> parents = ContainerUtil.map(record.getParentsHashes(), factory::createHash);

    return new GitCommit(project, HashImpl.build(record.getHash()), parents, record.getCommitTime(), root, record.getSubject(),
                         factory.createUser(record.getAuthorName(), record.getAuthorEmail()), record.getFullMessage(),
                         factory.createUser(record.getCommitterName(), record.getCommitterEmail()), record.getAuthorTimeStamp(),
                         ContainerUtil.map(records, GitLogRecord::getStatusInfos), renameLimit);
  }
}
