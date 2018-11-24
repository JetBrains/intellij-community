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
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ObjectUtils.notNull;
import static git4idea.history.GitLogParser.GitLogOption.HASH;
import static git4idea.history.GitLogParser.GitLogOption.TREE;

/*
 * This class collects records for one commit and sends them together for processing.
 * It also deals with problems with `-m` flag, when `git log` does not provide empty records when a commit is not different from one of the parents.
 */
abstract class GitLogRecordCollector implements Consumer<GitLogRecord> {
  private static final Logger LOG = Logger.getInstance(GitLogRecordCollector.class);
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final MultiMap<String, GitLogRecord> myHashToRecord = MultiMap.createLinked();

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
        processCollectedRecords();
      }
    }
  }

  private void processCollectedRecords() {
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
        if (!fillWithEmptyRecords(records)) continue; // skipping commit altogether on error
      }
      consume(records);
    }
    myHashToRecord.clear();
  }

  public void finish() {
    processCollectedRecords();
  }

  public abstract void consume(@NotNull List<GitLogRecord> records);

  /*
   * This method calculates tree hashes for a commit and its parents and places an empty record for parents that have same tree hash.
   */
  private boolean fillWithEmptyRecords(@NotNull List<GitLogRecord> records) {
    GitLogRecord firstRecord = records.get(0);
    String commit = firstRecord.getHash();
    String[] parents = firstRecord.getParentsHashes();

    List<String> hashes = ContainerUtil.newArrayList(parents);
    hashes.add(commit);

    GitSimpleHandler handler = new GitSimpleHandler(myProject, myRoot, GitCommand.LOG);
    GitLogParser parser = new GitLogParser(myProject, GitLogParser.NameStatus.NONE, HASH, TREE);
    handler.setStdoutSuppressed(true);
    handler.addParameters(parser.getPretty());
    handler.addParameters(GitHistoryUtils.formHashParameters(notNull(GitVcs.getInstance(myProject)), hashes));
    handler.endOptions();

    try {
      String output = handler.run();

      List<GitLogRecord> hashAndTreeRecords = parser.parse(output);
      Map<String, String> hashToTreeMap = ContainerUtil.map2Map(hashAndTreeRecords,
                                                                record -> Pair.create(record.getHash(), record.getTreeHash()));
      String commitTreeHash = hashToTreeMap.get(commit);
      LOG.assertTrue(commitTreeHash != null, "Could not get tree hash for commit " + commit);

      for (int parentIndex = 0; parentIndex < parents.length; parentIndex++) {
        String parent = parents[parentIndex];
        // sometimes a merge commit is identical to all its parents
        // in this case, we get one empty record
        String parentTreeHash = hashToTreeMap.get(parent);
        LOG.assertTrue(parentTreeHash != null, "Could not get tree hash for commit " + parent);
        if (parentTreeHash.equals(commitTreeHash) && records.size() < parents.length) {
          records.add(parentIndex, new GitLogRecord(firstRecord.getOptions(), ContainerUtil.emptyList(), ContainerUtil.emptyList(),
                                                    firstRecord.isSupportsRawBody()));
        }
      }
    }
    catch (VcsException e) {
      LOG.error(e);
      return false;
    }
    return true;
  }
}
