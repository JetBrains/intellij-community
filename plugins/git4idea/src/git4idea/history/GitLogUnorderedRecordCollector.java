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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class GitLogUnorderedRecordCollector extends GitLogRecordCollector {
  private static final Logger LOG = Logger.getInstance(GitLogUnorderedRecordCollector.class);
  private static final int STATUS_LINES_THRESHOLD = 20_000;

  @NotNull private final MultiMap<String, GitLogRecord> myHashToIncompleteRecords = MultiMap.createLinked();
  private int myIncompleteStatusLinesCount = 0;

  protected GitLogUnorderedRecordCollector(@NotNull Project project,
                                           @NotNull VirtualFile root,
                                           @NotNull Consumer<List<GitLogRecord>> consumer) {
    super(project, root, consumer);
  }

  @Override
  protected void processCollectedRecords() {
    processCollectedRecords(false);
  }

  private void processCollectedRecords(boolean processIncompleteRecords) {
    super.processCollectedRecords();

    // we want to avoid executing a lot of "git log" commands
    // at the same time we do not want to waste too much memory on records
    // so we process "incomplete" records when we accumulate too much of them in terms of status lines
    // or at the very end
    if (!myHashToIncompleteRecords.isEmpty() && (processIncompleteRecords || myIncompleteStatusLinesCount >= STATUS_LINES_THRESHOLD)) {
      try {
        processIncompleteRecords(myHashToIncompleteRecords, myProject, myRoot, myConsumer);
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

  @Override
  protected void processIncompleteRecord(@NotNull String hash, @NotNull List<GitLogRecord> records) {
    myHashToIncompleteRecords.put(hash, records);
    records.forEach(r -> myIncompleteStatusLinesCount += r.getStatusInfos().size());
  }

  @Override
  public void finish() {
    processCollectedRecords(true);
  }
}
