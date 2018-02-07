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
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.ObjectUtils.notNull;
import static git4idea.history.GitLogParser.GitLogOption.HASH;
import static git4idea.history.GitLogParser.GitLogOption.TREE;

/*
 * This class collects records for one commit and sends them together for processing.
 * It also deals with problems with `-m` flag, when `git log` does not provide empty records when a commit is not different from one of the parents.
 */
abstract class GitLogRecordCollector implements Consumer<GitLogRecord> {
  private static final Logger LOG = Logger.getInstance(GitLogRecordCollector.class);
  private static final int STATUS_LINES_THRESHOLD = 20_000;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final MultiMap<String, GitLogRecord> myHashToRecord = MultiMap.create();
  @NotNull private final MultiMap<String, GitLogRecord> myHashToIncompleteRecords = MultiMap.create();
  private int myIncompleteStatusLinesCount = 0;

  protected GitLogRecordCollector(@NotNull Project project, @NotNull VirtualFile root) {
    myProject = project;
    myRoot = root;
  }

  @Override
  public void consume(@NotNull GitLogRecord record) {
    String[] parents = record.getParentsHashes();
    if (parents.length <= 1) {
      consume(Collections.singletonList(record));
    }
    else {
      myHashToRecord.putValue(record.getHash(), record);
      if (parents.length == myHashToRecord.get(record.getHash()).size()) {
        processCollectedRecords(false);
      }
    }
  }

  private void processCollectedRecords(boolean processIncompleteRecords) {
    // there is a surprising (or not really surprising, depending how to look at it) problem with `-m` option
    // despite what is written in git-log documentation, it does not always output a record for each parent of a merge commit
    // if a merge commit has no changes with one of the parents, nothing is output for that parent
    // there is no way of knowing which parent it is just from git-log output
    // if we did not use custom pretty format, line `from <hash>` would appear in the record header
    // but we use, so there is no hash in the record header
    // and there is no format option to display it
    // so the solution is to run another git log command and get tree hashes for all participating commits
    // tree hashes allow to determine, which parent of the commit in question is the same as the commit itself and create an empty record for it
    for (String hash : myHashToRecord.keySet()) {
      ArrayList<GitLogRecord> records = ContainerUtil.newArrayList(notNull(myHashToRecord.get(hash)));
      GitLogRecord firstRecord = records.get(0);
      if (firstRecord.getParentsHashes().length != 0 && records.size() != firstRecord.getParentsHashes().length) {
        myHashToIncompleteRecords.put(hash, records);
        records.forEach(r -> myIncompleteStatusLinesCount += r.getStatusInfos().size());
      }
      else {
        consume(records);
      }
    }
    myHashToRecord.clear();

    // we want to avoid executing a lot of "git log" commands
    // at the same time we do not want to waste too much memory on records
    // so we process "incomplete" records when we accumulate too much of them in terms of status lines
    // or at the very end
    if (!myHashToIncompleteRecords.isEmpty() && (processIncompleteRecords || myIncompleteStatusLinesCount >= STATUS_LINES_THRESHOLD)) {
      try {
        Map<String, String> hashToTreeMap = getHashToTreeMap(ContainerUtil.map(myHashToIncompleteRecords.entrySet(),
                                                                               e -> ContainerUtil.getFirstItem(e.getValue())));
        for (String hash : myHashToIncompleteRecords.keySet()) {
          ArrayList<GitLogRecord> records = ContainerUtil.newArrayList(notNull(myHashToIncompleteRecords.get(hash)));
          fillWithEmptyRecords(records, hashToTreeMap);
          consume(records);
        }
      }
      catch (VcsException e) {
        LOG.error(e);
      }
      finally {
        myHashToIncompleteRecords.clear();
        myIncompleteStatusLinesCount = 0;
        // do not keep records on error
      }
    }
  }

  public void finish() {
    processCollectedRecords(true);
  }

  public abstract void consume(@NotNull List<GitLogRecord> records);

  /*
   * This method calculates tree hashes for commits and their parents.
   */
  @NotNull
  private Map<String, String> getHashToTreeMap(@NotNull Collection<GitLogRecord> records) throws VcsException {
    Set<String> hashes = ContainerUtil.newHashSet();

    for (GitLogRecord r : records) {
      hashes.add(r.getHash());
      ContainerUtil.addAll(hashes, r.getParentsHashes());
    }

    GitLineHandler handler = new GitLineHandler(myProject, myRoot, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(myProject, GitLogParser.NameStatus.NONE, HASH, TREE);
    GitVcs vcs = GitVcs.getInstance(myProject);
    handler.setStdoutSuppressed(true);
    handler.addParameters(parser.getPretty());
    handler.addParameters(GitLogUtil.getNoWalkParameter(vcs));
    handler.addParameters(GitLogUtil.STDIN);
    handler.endOptions();

    GitLogUtil.sendHashesToStdin(vcs, hashes, handler);
    String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

    if (!handler.errors().isEmpty()) {
      throw new VcsException(GitUIUtil.stringifyErrors(handler.errors()));
    }

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
