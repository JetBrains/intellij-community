// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class GitLogUnorderedRecordCollector extends GitLogRecordCollector<GitCompressedRecord> {
  private static final Logger LOG = Logger.getInstance(GitLogUnorderedRecordCollector.class);
  private static final int STATUS_LINES_THRESHOLD = 200_000;

  @NotNull private final MultiMap<String, GitCompressedRecord> myHashToIncompleteRecords = MultiMap.createLinked();
  private int myIncompleteStatusLinesCount = 0;

  GitLogUnorderedRecordCollector(@NotNull Project project,
                                           @NotNull VirtualFile root,
                                           @NotNull Consumer<? super List<GitCompressedRecord>> consumer) {
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
  protected void processIncompleteRecord(@NotNull String hash, @NotNull List<GitCompressedRecord> records) {
    myHashToIncompleteRecords.put(hash, records);
    records.forEach(r -> myIncompleteStatusLinesCount += r.getChanges().size() + r.getRenames().size());
  }

  @Override
  protected GitCompressedRecord createEmptyCopy(@NotNull GitCompressedRecord record) {
    return new GitCompressedRecord(record.getOptions(), new Int2ObjectOpenHashMap<>(), new Int2IntOpenHashMap(), record.isSupportsRawBody());
  }

  @Override
  public void finish() {
    processCollectedRecords(true);
  }
}
