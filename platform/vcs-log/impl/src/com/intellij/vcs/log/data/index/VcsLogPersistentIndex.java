/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.PersistentMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.impl.FatalErrorConsumer;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl;
import com.intellij.vcs.log.util.PersistentUtil;
import com.intellij.vcs.log.util.StopWatch;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class VcsLogPersistentIndex implements VcsLogIndex, Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogPersistentIndex.class);
  private static final int BATCH_SIZE = 1000;
  private static final int VERSION = 0;

  @NotNull private final Project myProject;
  @NotNull private final FatalErrorConsumer myFatalErrorsConsumer;
  @NotNull private final VcsLogProgress myProgress;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myProviders;
  @NotNull private final VcsLogStorage myHashMap;
  @NotNull private final VcsUserRegistryImpl myUserRegistry;
  @NotNull private final Set<VirtualFile> myRoots;

  @NotNull private final PersistentMap<Integer, String> myMessagesIndex;
  @Nullable private final VcsLogMessagesTrigramIndex myTrigramIndex;
  @Nullable private final VcsLogUserIndex myUserIndex;
  @Nullable private final VcsLogPathsIndex myPathsIndex;

  @NotNull private final SingleTaskController<IndexingRequest, Void> mySingleTaskController = new MySingleTaskController();
  @NotNull private final Map<VirtualFile, AtomicInteger> myNumberOfTasks = ContainerUtil.newHashMap();

  @NotNull private Map<VirtualFile, TIntHashSet> myCommitsToIndex = ContainerUtil.newHashMap();

  public VcsLogPersistentIndex(@NotNull Project project,
                               @NotNull VcsLogStorage hashMap,
                               @NotNull VcsLogProgress progress,
                               @NotNull Map<VirtualFile, VcsLogProvider> providers,
                               @NotNull FatalErrorConsumer fatalErrorsConsumer,
                               @NotNull Disposable disposableParent) {
    myHashMap = hashMap;
    myProject = project;
    myProgress = progress;
    myProviders = providers;
    myFatalErrorsConsumer = fatalErrorsConsumer;
    myRoots = ContainerUtil.newLinkedHashSet();

    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      if (VcsLogProperties.get(entry.getValue(), VcsLogProperties.SUPPORTS_INDEXING)) {
        myRoots.add(entry.getKey());
      }
    }

    myUserRegistry = (VcsUserRegistryImpl)ServiceManager.getService(myProject, VcsUserRegistry.class);

    String logId = PersistentUtil.calcLogId(myProject, providers);

    myMessagesIndex = createMap(EnumeratorStringDescriptor.INSTANCE, "messages", logId, 0);
    myTrigramIndex = createIndex(() -> new VcsLogMessagesTrigramIndex(logId, this));
    myUserIndex = createIndex(() -> new VcsLogUserIndex(logId, myUserRegistry, fatalErrorsConsumer, this));
    myPathsIndex = createIndex(() -> new VcsLogPathsIndex(logId, myRoots, fatalErrorsConsumer, this));

    for (VirtualFile root : myRoots) {
      myNumberOfTasks.put(root, new AtomicInteger());
    }

    Disposer.register(disposableParent, this);
  }

  public static int getVersion() {
    return VcsLogStorageImpl.VERSION + VERSION;
  }

  @Nullable
  private <I extends VcsLogFullDetailsIndex> I createIndex(@NotNull ThrowableComputable<I, IOException> computable) {
    try {
      return computable.compute();
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return null;
  }

  @NotNull
  private <V> PersistentMap<Integer, V> createMap(@NotNull KeyDescriptor<V> descriptor,
                                                  @NotNull String kind,
                                                  @NotNull String logId, int version) {
    try {
      return PersistentUtil.createPersistentHashMap(descriptor, kind, logId, version);
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
      return new InMemoryMap<>();
    }
  }

  @Override
  public synchronized void scheduleIndex(boolean full) {
    if (myCommitsToIndex.isEmpty()) return;
    Map<VirtualFile, TIntHashSet> commitsToIndex = myCommitsToIndex;

    for (VirtualFile root : commitsToIndex.keySet()) {
      myNumberOfTasks.get(root).incrementAndGet();
    }
    myCommitsToIndex = ContainerUtil.newHashMap();

    mySingleTaskController.request(new IndexingRequest(commitsToIndex, full));
  }

  private void storeDetails(@NotNull List<? extends VcsFullCommitDetails> details, boolean flush) {
    try {
      for (VcsFullCommitDetails detail : details) {
        int index = myHashMap.getCommitIndex(detail.getId(), detail.getRoot());

        myMessagesIndex.put(index, detail.getFullMessage());
        if (myTrigramIndex != null) myTrigramIndex.update(index, detail);
        if (myUserIndex != null) myUserIndex.update(index, detail);
        if (myPathsIndex != null) myPathsIndex.update(index, detail);
      }
      if (flush) {
        flush();
      }
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
  }

  private void flush() {
    try {
      myMessagesIndex.force();
      if (myTrigramIndex != null) myTrigramIndex.flush();
      if (myUserIndex != null) myUserIndex.flush();
      if (myPathsIndex != null) myPathsIndex.flush();
    }
    catch (StorageException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
  }

  public void markCorrupted() {
    if (myMessagesIndex instanceof PersistentHashMap) ((PersistentHashMap)myMessagesIndex).markCorrupted();
    if (myTrigramIndex != null) myTrigramIndex.markCorrupted();
    if (myUserIndex != null) myUserIndex.markCorrupted();
    if (myPathsIndex != null) myPathsIndex.markCorrupted();
  }

  @Override
  public boolean isIndexed(int commit) {
    try {
      return myMessagesIndex.get(commit) != null &&
             (myUserIndex == null || myUserIndex.isIndexed(commit)) &&
             (myPathsIndex == null || myPathsIndex.isIndexed(commit)) &&
             (myTrigramIndex == null || myTrigramIndex.isIndexed(commit));
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }
    return false;
  }

  @Override
  public synchronized boolean isIndexed(@NotNull VirtualFile root) {
    return myRoots.contains(root) && (!myCommitsToIndex.containsKey(root) && myNumberOfTasks.get(root).get() == 0);
  }

  @Override
  public synchronized void markForIndexing(int index, @NotNull VirtualFile root) {
    if (isIndexed(index) || !myRoots.contains(root)) return;
    TIntHashSet set = myCommitsToIndex.get(root);
    if (set == null) {
      set = new TIntHashSet();
      myCommitsToIndex.put(root, set);
    }
    set.add(index);
  }

  @NotNull
  private <T> TIntHashSet filter(@NotNull PersistentMap<Integer, T> map, @NotNull Condition<T> condition) {
    TIntHashSet result = new TIntHashSet();
    try {
      Processor<Integer> processor = integer -> {
        try {
          T value = map.get(integer);
          if (value != null) {
            if (condition.value(value)) {
              result.add(integer);
            }
          }
        }
        catch (IOException e) {
          myFatalErrorsConsumer.consume(this, e);
          return false;
        }
        return true;
      };
      if (myMessagesIndex instanceof PersistentHashMap) {
        ((PersistentHashMap<Integer, T>)myMessagesIndex).processKeysWithExistingMapping(processor);
      }
      else {
        myMessagesIndex.processKeys(processor);
      }
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
    }

    return result;
  }

  @NotNull
  private TIntHashSet filterUsers(@NotNull Set<VcsUser> users) {
    if (myUserIndex != null) {
      try {
        return myUserIndex.getCommitsForUsers(users);
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }
    return new TIntHashSet();
  }

  @NotNull
  private TIntHashSet filterPaths(@NotNull Collection<FilePath> paths) {
    if (myPathsIndex != null) {
      try {
        return myPathsIndex.getCommitsForPaths(paths);
      }
      catch (IOException | StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }
    return new TIntHashSet();
  }

  @NotNull
  public TIntHashSet filterMessages(@NotNull String text) {
    if (myTrigramIndex != null) {
      try {
        ValueContainer.IntIterator commitsForSearch = myTrigramIndex.getCommitsForSubstring(text);
        if (commitsForSearch != null) {
          TIntHashSet result = new TIntHashSet();
          while (commitsForSearch.hasNext()) {
            int commit = commitsForSearch.next();
            try {
              String value = myMessagesIndex.get(commit);
              if (value != null) {
                if (StringUtil.containsIgnoreCase(value, text)) {
                  result.add(commit);
                }
              }
            }
            catch (IOException e) {
              myFatalErrorsConsumer.consume(this, e);
              break;
            }
          }
          return result;
        }
      }
      catch (StorageException e) {
        myFatalErrorsConsumer.consume(this, e);
      }
    }

    return filter(myMessagesIndex, message -> StringUtil.containsIgnoreCase(message, text));
  }

  @Override
  public boolean canFilter(@NotNull List<VcsLogDetailsFilter> filters) {
    if (filters.isEmpty()) return false;
    for (VcsLogDetailsFilter filter : filters) {
      if (filter instanceof VcsLogTextFilter && myTrigramIndex != null && !VcsLogUtil.isRegexp(((VcsLogTextFilter)filter).getText()) ||
          filter instanceof VcsLogUserFilterImpl && myUserIndex != null ||
          filter instanceof VcsLogStructureFilter && myPathsIndex != null) {
        continue;
      }
      return false;
    }
    return true;
  }

  @Override
  @NotNull
  public Set<Integer> filter(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    VcsLogTextFilter textFilter = ContainerUtil.findInstance(detailsFilters, VcsLogTextFilter.class);
    VcsLogUserFilterImpl userFilter = ContainerUtil.findInstance(detailsFilters, VcsLogUserFilterImpl.class);
    VcsLogStructureFilter pathFilter = ContainerUtil.findInstance(detailsFilters, VcsLogStructureFilter.class);

    TIntHashSet filteredByMessage = null;
    if (textFilter != null) {
      filteredByMessage = filterMessages(textFilter.getText());
    }

    TIntHashSet filteredByUser = null;
    if (userFilter != null) {
      Set<VcsUser> users = ContainerUtil.newHashSet();
      for (VirtualFile root : myRoots) {
        users.addAll(userFilter.getUsers(root));
      }

      filteredByUser = filterUsers(users);
    }

    TIntHashSet filteredByPath = null;
    if (pathFilter != null) {
      filteredByPath = filterPaths(pathFilter.getFiles());
    }

    return TroveUtil.intersect(filteredByMessage, filteredByPath, filteredByUser);
  }

  @Override
  public void dispose() {
    try {
      myMessagesIndex.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public void printDebugInfoForCommit(@NotNull CommitId commitId) {
    int commit = myHashMap.getCommitIndex(commitId.getHash(), commitId.getRoot());
    if (!isIndexed(commit)) {
      LOG.info("Commit " + commitId.getHash().asString() + " is not indexed.");
    }
    else {
      StringBuilder builder = new StringBuilder();
      try {
        builder.append("Commit ").append(commitId.getHash().asString()).append(" index info:\n");

        builder.append("Message:\n").append(myMessagesIndex.get(commit)).append("\n");
        if (myTrigramIndex != null) {
          builder.append("Trigrams:\n").append(myTrigramIndex.getTrigramInfo(commit)).append("\n");
        }
        else {
          builder.append("Trigrams index is null");
        }
        if (myUserIndex != null) {
          builder.append("User:\n").append(myUserIndex.getUserInfo(commit)).append("\n");
        }
        else {
          builder.append("User index is null");
        }
        if (myPathsIndex != null) {
          builder.append("Paths:\n").append(myPathsIndex.getPathInfo(commit)).append("\n");
        }
        else {
          builder.append("Paths index is null");
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }

      LOG.info(builder.toString());
    }
  }

  private class MySingleTaskController extends SingleTaskController<IndexingRequest, Void> {
    public MySingleTaskController() {
      super(EmptyConsumer.getInstance());
    }

    @Override
    protected void startNewBackgroundTask() {
      ApplicationManager.getApplication().invokeLater(() -> {
        Task.Backgroundable task = new Task.Backgroundable(VcsLogPersistentIndex.this.myProject, "Indexing Commit Data", true,
                                                           PerformInBackgroundOption.ALWAYS_BACKGROUND) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            List<IndexingRequest> requests;
            while (!(requests = popRequests()).isEmpty()) {
              for (IndexingRequest request : requests) {
                try {
                  request.run(indicator);
                }
                catch (ProcessCanceledException reThrown) {
                  throw reThrown;
                }
                catch (Throwable t) {
                  LOG.error("Error while indexing", t);
                }
              }
            }

            taskCompleted(null);
          }
        };
        ProgressIndicator indicator = myProgress.createProgressIndicator(false);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
      });
    }
  }

  private class IndexingRequest {
    private static final int MAGIC_NUMBER = 150000;
    private final Map<VirtualFile, TIntHashSet> myCommits;
    private final boolean myFull;

    public IndexingRequest(@NotNull Map<VirtualFile, TIntHashSet> commits, boolean full) {
      myCommits = commits;
      myFull = full;
    }

    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setFraction(0);

      long time = System.currentTimeMillis();

      CommitsCounter counter = new CommitsCounter(indicator, myCommits.values().stream().mapToInt(TIntHashSet::size).sum());
      LOG.debug("Indexing " + counter.allCommits + " commits");

      for (VirtualFile root : myCommits.keySet()) {
        try {
          if (myFull) {
            indexAll(root, myCommits.get(root), counter);
          }
          else {
            indexOneByOne(root, myCommits.get(root), counter);
          }
        }
        finally {
          myNumberOfTasks.get(root).decrementAndGet();
        }
      }

      LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - time) +
                " for indexing " +
                counter.newIndexedCommits +
                " new commits out of " +
                counter.allCommits);
      int leftCommits = counter.allCommits - counter.newIndexedCommits - counter.oldCommits;
      if (leftCommits > 0) {
        LOG.warn("Did not index " + leftCommits + " commits");
      }
    }

    private void indexOneByOne(@NotNull VirtualFile root,
                               @NotNull TIntHashSet commitsSet,
                               @NotNull CommitsCounter counter) {
      IntStream commits = TroveUtil.stream(commitsSet).filter(c -> {
        if (isIndexed(c)) {
          counter.oldCommits++;
          return false;
        }
        return true;
      });

      indexOneByOne(root, counter, commits);
    }

    private void indexOneByOne(@NotNull VirtualFile root,
                               @NotNull CommitsCounter counter,
                               @NotNull IntStream commits) {
      TroveUtil.processBatches(commits, BATCH_SIZE, batch -> {
        counter.indicator.checkCanceled();

        if (indexOneByOne(root, batch)) {
          counter.newIndexedCommits += batch.size();
        }

        counter.displayProgress();
      });
    }

    private boolean indexOneByOne(@NotNull VirtualFile root, @NotNull TIntHashSet commits) {
      VcsLogProvider provider = myProviders.get(root);
      try {
        storeDetails(provider.readFullDetails(root, TroveUtil.map(commits, value -> myHashMap.getCommitId(value).getHash().asString())),
                     true);
      }
      catch (VcsException e) {
        LOG.error(e);
        commits.forEach(value -> {
          markForIndexing(value, root);
          return true;
        });
        return false;
      }
      return true;
    }

    public void indexAll(@NotNull VirtualFile root,
                         @NotNull TIntHashSet commitsSet,
                         @NotNull CommitsCounter counter) {
      TIntHashSet notIndexed = new TIntHashSet();
      TroveUtil.stream(commitsSet).forEach(c -> {
        if (isIndexed(c)) {
          counter.oldCommits++;
        }
        else {
          notIndexed.add(c);
        }
      });
      counter.displayProgress();

      if (notIndexed.size() <= MAGIC_NUMBER) {
        indexOneByOne(root, counter, TroveUtil.stream(notIndexed));
      }
      else {
        try {
          myProviders.get(root).readAllFullDetails(root, details -> {
            int index = myHashMap.getCommitIndex(details.getId(), details.getRoot());
            if (notIndexed.contains(index)) {
              storeDetails(Collections.singletonList(details), false);
              counter.newIndexedCommits++;
            }

            counter.indicator.checkCanceled();
            counter.displayProgress();
          });
        }
        catch (VcsException e) {
          LOG.error(e);
          notIndexed.forEach(value -> {
            markForIndexing(value, root);
            return true;
          });
        }
      }

      flush();
    }
  }

  private static class CommitsCounter {
    @NotNull public final ProgressIndicator indicator;
    public final int allCommits;
    public volatile int newIndexedCommits;
    public volatile int oldCommits;

    private CommitsCounter(@NotNull ProgressIndicator indicator, int commits) {
      this.indicator = indicator;
      this.allCommits = commits;
    }

    public void displayProgress() {
      indicator.setFraction(((double)newIndexedCommits + oldCommits) / allCommits);
    }
  }
}
