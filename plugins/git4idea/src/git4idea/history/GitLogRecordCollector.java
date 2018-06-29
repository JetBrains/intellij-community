// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;
import static git4idea.history.GitLogParser.GitLogOption.HASH;
import static git4idea.history.GitLogParser.GitLogOption.TREE;

/*
 * This class collects records for one commit and sends them together for processing.
 * It also deals with problems with `-m` flag, when `git log` does not provide empty records when a commit is not different from one of the parents.
 */
class GitLogRecordCollector implements Consumer<GitLogRecord> {
  private static final Logger LOG = Logger.getInstance(GitLogRecordCollector.class);

  @NotNull protected final Project myProject;
  @NotNull protected final VirtualFile myRoot;
  @NotNull protected final Consumer<List<GitLogRecord>> myConsumer;

  @NotNull private final MultiMap<String, GitLogRecord> myHashToRecord = MultiMap.createLinked();
  @Nullable private String myLastHash = null;

  protected GitLogRecordCollector(@NotNull Project project,
                                  @NotNull VirtualFile root,
                                  @NotNull Consumer<List<GitLogRecord>> consumer) {
    myProject = project;
    myRoot = root;
    myConsumer = consumer;
  }

  @Override
  public void consume(@NotNull GitLogRecord record) {
    if (!record.getHash().equals(myLastHash)) {
      processCollectedRecords();
    }
    myLastHash = record.getHash();

    myHashToRecord.putValue(record.getHash(), record);
    if (record.getParentsHashes().length == myHashToRecord.get(record.getHash()).size()) {
      processCollectedRecords();
    }
  }

  public void finish() {
    processCollectedRecords();
  }

  protected void processCollectedRecords() {
    for (String hash : myHashToRecord.keySet()) {
      ArrayList<GitLogRecord> records = ContainerUtil.newArrayList(notNull(myHashToRecord.get(hash)));
      GitLogRecord firstRecord = records.get(0);
      if (firstRecord.getParentsHashes().length != 0 && records.size() != firstRecord.getParentsHashes().length) {
        processIncompleteRecord(hash, records);
      }
      else {
        myConsumer.consume(records);
      }
    }
    myHashToRecord.clear();
  }

  protected void processIncompleteRecord(@NotNull String hash, @NotNull List<GitLogRecord> records) {
    // there is a surprising (or not really surprising, depending how to look at it) problem with `-m` option
    // despite what is written in git-log documentation, it does not always output a record for each parent of a merge commit
    // if a merge commit has no changes with one of the parents, nothing is output for that parent
    // there is no way of knowing which parent it is just from git-log output
    // if we did not use custom pretty format, line `from <hash>` would appear in the record header
    // but we use, so there is no hash in the record header
    // and there is no format option to display it
    // so the solution is to run another git log command and get tree hashes for all participating commits
    // tree hashes allow to determine, which parent of the commit in question is the same as the commit itself and create an empty record for it
    MultiMap<String, GitLogRecord> incompleteRecords = MultiMap.create();
    incompleteRecords.put(hash, records);
    try {
      processIncompleteRecords(incompleteRecords, myProject, myRoot, myConsumer);
    }
    catch (VcsException e) {
      LOG.error(e);
    }
  }

  public static void processIncompleteRecords(@NotNull MultiMap<String, GitLogRecord> incompleteRecords,
                                              @NotNull Project project,
                                              @NotNull VirtualFile root,
                                              @NotNull Consumer<List<GitLogRecord>> consumer) throws VcsException {
    List<GitLogRecord> firstRecords = ContainerUtil.map(incompleteRecords.entrySet(), e -> ContainerUtil.getFirstItem(e.getValue()));
    Map<String, String> hashToTreeMap = getHashToTreeMap(project, root, firstRecords);
    for (String hash : incompleteRecords.keySet()) {
      ArrayList<GitLogRecord> records = ContainerUtil.newArrayList(notNull(incompleteRecords.get(hash)));
      fillWithEmptyRecords(records, hashToTreeMap);
      consumer.consume(records);
    }
  }

  /*
   * This method calculates tree hashes for commits and their parents.
   */
  @NotNull
  private static Map<String, String> getHashToTreeMap(@NotNull Project project,
                                                      @NotNull VirtualFile root,
                                                      @NotNull Collection<GitLogRecord> records)
    throws VcsException {
    Set<String> hashes = ContainerUtil.newHashSet();

    for (GitLogRecord r : records) {
      hashes.add(r.getHash());
      ContainerUtil.addAll(hashes, r.getParentsHashes());
    }

    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(project, GitLogParser.NameStatus.NONE, HASH, TREE);
    GitVcs vcs = GitVcs.getInstance(project);
    handler.setStdoutSuppressed(true);
    handler.addParameters(parser.getPretty());
    handler.addParameters(GitLogUtil.getNoWalkParameter(vcs));
    handler.addParameters(GitLogUtil.STDIN);
    handler.endOptions();

    GitLogUtil.sendHashesToStdin(vcs, hashes, handler);
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    List<GitLogRecord> hashAndTreeRecords = parser.parse(output);
    return ContainerUtil.map2Map(hashAndTreeRecords, record -> Pair.create(record.getHash(), record.getTreeHash()));
  }

  /*
   * This method places an empty record for parents that have same tree hash.
   */
  private static void fillWithEmptyRecords(@NotNull List<GitLogRecord> records, @NotNull Map<String, String> hashToTreeMap) {
    GitLogRecord firstRecord = records.get(0);
    String commit = firstRecord.getHash();
    String[] parents = firstRecord.getParentsHashes();

    String commitTreeHash = hashToTreeMap.get(commit);
    LOG.assertTrue(commitTreeHash != null, "Could not get tree hash for commit " + commit);

    for (int parentIndex = 0; parentIndex < parents.length; parentIndex++) {
      String parent = parents[parentIndex];
      // sometimes a merge commit is identical to all its parents
      // in this case, we get one empty record
      String parentTreeHash = hashToTreeMap.get(parent);
      LOG.assertTrue(parentTreeHash != null, "Could not get tree hash for commit " + parent);
      if (parentTreeHash.equals(commitTreeHash) && records.size() < parents.length) {
        records.add(parentIndex, new GitLogRecord(firstRecord.getOptions(), ContainerUtil.emptyList(), firstRecord.isSupportsRawBody()));
      }
    }
  }
}
