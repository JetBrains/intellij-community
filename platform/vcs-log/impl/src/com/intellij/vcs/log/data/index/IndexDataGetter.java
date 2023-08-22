// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.history.EdgeData;
import com.intellij.vcs.log.history.FileHistoryData;
import com.intellij.vcs.log.history.VcsDirectoryRenamesProvider;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.util.IntCollectionUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcs.log.visible.filters.VcsLogMultiplePatternsTextFilter;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.vcs.log.data.index.PhmVcsLogStorageBackendKt.getHashes;
import static com.intellij.vcs.log.history.FileHistoryKt.FILE_PATH_HASHING_STRATEGY;

public final class IndexDataGetter {
  private static final Logger LOG = Logger.getInstance(IndexDataGetter.class);
  private final @NotNull Project myProject;
  private final @NotNull Map<VirtualFile, VcsLogProvider> myProviders;
  private final @NotNull VcsLogStorageBackend myIndexStorageBackend;
  private final @NotNull VcsLogStorage myLogStorage;
  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private final @NotNull VcsDirectoryRenamesProvider myDirectoryRenamesProvider;
  private final boolean myIsProjectLog;

  public IndexDataGetter(@NotNull Project project,
                         @NotNull Map<VirtualFile, VcsLogProvider> providers,
                         @NotNull VcsLogStorageBackend indexStorageBackend,
                         @NotNull VcsLogStorage logStorage,
                         @NotNull VcsLogErrorHandler errorHandler) {
    myProject = project;
    myProviders = providers;
    myIndexStorageBackend = indexStorageBackend;
    myLogStorage = logStorage;
    myErrorHandler = errorHandler;

    myDirectoryRenamesProvider = VcsDirectoryRenamesProvider.getInstance(myProject);

    myIsProjectLog = Arrays.stream(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots())
      .map(VcsRoot::getPath)
      .collect(Collectors.toSet())
      .containsAll(myProviders.keySet());
  }

  void iterateIndexedCommits(int limit, @NotNull IntFunction<Boolean> processor) {
    executeAndCatch(() -> myIndexStorageBackend.iterateIndexedCommits(limit, processor));
  }

  //
  // Getters from forward index
  //

  public @Nullable VcsUser getAuthor(int commit) {
    return executeAndCatch(() -> myIndexStorageBackend.getAuthorForCommit(commit));
  }

  public @Nullable Map<Integer, VcsUser> getAuthor(@NotNull Collection<Integer> commitIds) {
    return executeAndCatch(() -> myIndexStorageBackend.getAuthorForCommits(commitIds));
  }

  public @Nullable VcsUser getCommitter(int commit) {
    return executeAndCatch(() -> {
      return myIndexStorageBackend.getCommitterForCommit(commit);
    });
  }

  public @NotNull Map<Integer, VcsUser> getCommitter(@NotNull Collection<Integer> commitIds) {
    return executeAndCatch(() -> myIndexStorageBackend.getCommitterForCommits(commitIds), Collections.emptyMap());
  }

  public @Nullable Long getAuthorTime(int commit) {
    return executeAndCatch(() -> {
      long[] time = myIndexStorageBackend.getTimestamp(commit);
      return time == null ? null : time[0];
    });
  }

  public @Nullable Map<Integer, Long> getAuthorTime(@NotNull Collection<Integer> commitIds) {
    return executeAndCatch(() -> myIndexStorageBackend.getAuthorTime(commitIds));
  }

  public @Nullable Long getCommitTime(int commit) {
    return executeAndCatch(() -> {
      long[] time = myIndexStorageBackend.getTimestamp(commit);
      return time == null ? null : time[1];
    });
  }

  public @Nullable Map<Integer, Long> getCommitTime(@NotNull Collection<Integer> commitIds) {
    return executeAndCatch(() -> myIndexStorageBackend.getCommitTime(commitIds));
  }

  public @Nullable String getFullMessage(int index) {
    return executeAndCatch(() -> myIndexStorageBackend.getMessage(index));
  }

  public @Nullable Map<Integer, String> getFullMessage(@NotNull Collection<Integer> commitIds) {
    return executeAndCatch(() -> myIndexStorageBackend.getMessages(commitIds));
  }

  public @Nullable List<Hash> getParents(int index) {
    return executeAndCatch(() -> {
      int[] parentsIndexes = myIndexStorageBackend.getParents(index);
      if (parentsIndexes == null) return null;
      return getHashes(myLogStorage, parentsIndexes);
    });
  }

  public @Nullable Map<Integer, List<Hash>> getParents(@NotNull Collection<Integer> commitIds) {
    return executeAndCatch(() -> myIndexStorageBackend.getParents(commitIds));
  }

  //
  // Filters
  //

  public boolean canFilter(@NotNull List<VcsLogDetailsFilter> filters) {
    if (filters.isEmpty()) return false;

    return ContainerUtil.all(filters, filter -> {
      if (filter instanceof VcsLogTextFilter ||
          filter instanceof VcsLogUserFilter) {
        return true;
      }
      if (filter instanceof VcsLogStructureFilter) {
        Collection<FilePath> files = ((VcsLogStructureFilter)filter).getFiles();
        return ContainerUtil.find(files, file -> file.isDirectory() && myProviders.containsKey(file.getVirtualFile())) == null;
      }
      return false;
    });
  }

  public @NotNull IntSet filter(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    return filter(detailsFilters, null);
  }

  public @NotNull IntSet filter(@NotNull List<VcsLogDetailsFilter> detailsFilters, @Nullable IntSet candidates) {
    VcsLogTextFilter textFilter = ContainerUtil.findInstance(detailsFilters, VcsLogTextFilter.class);
    VcsLogUserFilter userFilter = ContainerUtil.findInstance(detailsFilters, VcsLogUserFilter.class);
    VcsLogStructureFilter pathFilter = ContainerUtil.findInstance(detailsFilters, VcsLogStructureFilter.class);

    IntSet filteredByUser = null;
    if (userFilter != null) {
      Set<VcsUser> users = new HashSet<>();
      for (VirtualFile root : myProviders.keySet()) {
        users.addAll(userFilter.getUsers(root));
      }

      filteredByUser = filterUsers(users);
    }

    IntSet filteredByPath = null;
    if (pathFilter != null) {
      filteredByPath = filterPaths(pathFilter.getFiles());
    }

    IntSet filteredByUserAndPath = IntCollectionUtil.intersect(filteredByUser, filteredByPath, candidates);
    if (textFilter == null) {
      return filteredByUserAndPath == null ? IntSets.EMPTY_SET : filteredByUserAndPath;
    }
    return filterMessages(textFilter, filteredByUserAndPath);
  }

  private @NotNull IntSet filterUsers(@NotNull Set<? extends VcsUser> users) {
    return executeAndCatch(() -> myIndexStorageBackend.getCommitsForUsers(users), new IntOpenHashSet());
  }

  private @NotNull IntSet filterPaths(@NotNull Collection<? extends FilePath> paths) {
    return executeAndCatch(() -> {
      IntSet result = new IntOpenHashSet();
      for (FilePath path : paths) {
        result.addAll(createFileHistoryData(path).build().getCommits());
      }
      return result;
    }, new IntOpenHashSet());
  }

  private @NotNull IntSet filterMessages(@NotNull VcsLogTextFilter filter, @Nullable IntSet candidates) {
    IntSet result = new IntOpenHashSet();
    filterMessages(filter, candidates, result::add);
    return result;
  }

  public void filterMessages(@NotNull VcsLogTextFilter filter, @NotNull IntConsumer consumer) {
    filterMessages(filter, null, consumer);
  }

  private void filterMessages(@NotNull VcsLogTextFilter filter, @Nullable IntSet candidates, @NotNull IntConsumer consumer) {
    if (!filter.isRegex() || filter instanceof VcsLogMultiplePatternsTextFilter) {
      executeAndCatch(() -> {
        List<String> trigramSources = filter instanceof VcsLogMultiplePatternsTextFilter ?
                                      ((VcsLogMultiplePatternsTextFilter)filter).getPatterns() :
                                      Collections.singletonList(filter.getText());
        List<String> noTrigramSources = new ArrayList<>();
        for (String string : trigramSources) {
          myIndexStorageBackend.getCommitsForSubstring(string, candidates, noTrigramSources, consumer, filter);
        }

        if (!noTrigramSources.isEmpty()) {
          VcsLogTextFilter noTrigramFilter = VcsLogFilterObject.fromPatternsList(noTrigramSources, filter.matchesCase());
          filter(candidates, noTrigramFilter::matches, consumer);
        }
      });
    }
    else {
      executeAndCatch(() -> {
        filter(candidates, filter::matches, consumer);
      });
    }
  }

  private void filter(@Nullable IntIterable candidates,
                      @NotNull Predicate<String> condition,
                      @NotNull IntConsumer consumer) throws IOException {
    if (candidates == null) {
      myIndexStorageBackend.processMessages((commit, message) -> {
        if (message != null && condition.test(message)) {
          consumer.accept(commit);
        }
        return true;
      });
    }
    else {
      for (IntIterator iterator = candidates.iterator(); iterator.hasNext(); ) {
        int commit = iterator.nextInt();
        String value = myIndexStorageBackend.getMessage(commit);
        if (value != null && condition.test(value)) {
          consumer.accept(commit);
        }
      }
    }
  }

  //
  // File history
  //

  private @NotNull Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
    Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> affectedCommits = new Int2ObjectOpenHashMap<>();

    VirtualFile root = getRoot(path);
    if (myProviders.containsKey(root) && root != null) {
      executeAndCatch(() -> {
        myIndexStorageBackend.iterateChangesInCommits(root, path, (changes, commit) -> executeAndCatch(() -> {
          int[] parents = myIndexStorageBackend.getParents(commit);
          if (parents == null) {
            throw new CorruptedDataException("No parents for commit " + commit);
          }

          Int2ObjectMap<VcsLogPathsIndex.ChangeKind> changeMap = new Int2ObjectOpenHashMap<>(parents.length);
          if (parents.length == 0 && !changes.isEmpty()) {
            changeMap.put(commit, ContainerUtil.getFirstItem(changes));
          }
          else {
            if (parents.length != changes.size()) {
              throw new CorruptedDataException("Commit " + commit + " has " + parents.length +
                                               " parents, but " + changes.size() + " changes.");
            }
            for (int i = 0, length = parents.length; i < length; i++) {
              changeMap.put(parents[i], changes.get(i));
            }
          }

          affectedCommits.put(commit, changeMap);

          return null;
        }));
        return null;
      });
    }
    return affectedCommits;
  }

  public @NotNull FileHistoryData createFileHistoryData(@NotNull FilePath path) {
    return createFileHistoryData(Collections.singletonList(path));
  }

  public @NotNull FileHistoryData createFileHistoryData(@NotNull Collection<? extends FilePath> paths) {
    if (paths.size() == 1 && ContainerUtil.getFirstItem(paths).isDirectory()) {
      return new DirectoryHistoryData(ContainerUtil.getFirstItem(paths));
    }
    return new FileHistoryDataImpl(paths);
  }

  private class FileHistoryDataImpl extends FileHistoryData {
    private FileHistoryDataImpl(@NotNull FilePath startPath) {
      super(startPath);
    }

    private FileHistoryDataImpl(@NotNull Collection<? extends FilePath> startPaths) {
      super(startPaths);
    }

    @Override
    public @NotNull Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
      return IndexDataGetter.this.getAffectedCommits(path);
    }

    @Override
    public @Nullable EdgeData<FilePath> findRename(int parent, int child, @NotNull FilePath path, boolean isChildPath) {
      VirtualFile root = Objects.requireNonNull(getRoot(path));
      return executeAndCatch(() -> {
        return myIndexStorageBackend.findRename(parent, child, root, path, isChildPath);
      });
    }
  }

  private final class DirectoryHistoryData extends FileHistoryDataImpl {
    private final Map<EdgeData<Integer>, EdgeData<FilePath>> renamesMap = new HashMap<>();

    private DirectoryHistoryData(@NotNull FilePath startPath) {
      super(startPath);

      for (Map.Entry<EdgeData<CommitId>, EdgeData<FilePath>> entry : myDirectoryRenamesProvider.getRenamesMap().entrySet()) {
        EdgeData<CommitId> commits = entry.getKey();
        EdgeData<FilePath> rename = entry.getValue();
        if (VcsFileUtil.isAncestor(rename.child, startPath, false)) {
          FilePath renamedPath = VcsUtil.getFilePath(rename.parent.getPath() + "/" +
                                                     VcsFileUtil.relativePath(rename.child, startPath), true);
          renamesMap.put(new EdgeData<>(myLogStorage.getCommitIndex(commits.parent.getHash(), commits.parent.getRoot()),
                                        myLogStorage.getCommitIndex(commits.child.getHash(), commits.child.getRoot())),
                         new EdgeData<>(renamedPath, startPath));
        }
      }
    }

    @Override
    public @NotNull Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
      Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> affectedCommits = super.getAffectedCommits(path);
      if (!path.isDirectory()) return affectedCommits;
      hackAffectedCommits(path, affectedCommits);
      return affectedCommits;
    }

    private void hackAffectedCommits(@NotNull FilePath path,
                                     @NotNull Int2ObjectMap<Int2ObjectMap<VcsLogPathsIndex.ChangeKind>> affectedCommits) {
      for (Map.Entry<EdgeData<Integer>, EdgeData<FilePath>> entry : renamesMap.entrySet()) {
        int childCommit = entry.getKey().child;
        if (affectedCommits.containsKey(childCommit)) {
          EdgeData<FilePath> rename = entry.getValue();

          VcsLogPathsIndex.ChangeKind newKind;
          if (FILE_PATH_HASHING_STRATEGY.equals(rename.child, path)) {
            newKind = VcsLogPathsIndex.ChangeKind.ADDED;
          }
          else if (FILE_PATH_HASHING_STRATEGY.equals(rename.parent, path)) {
            newKind = VcsLogPathsIndex.ChangeKind.REMOVED;
          }
          else {
            continue;
          }

          Int2ObjectMap<VcsLogPathsIndex.ChangeKind> changesMap = affectedCommits.get(childCommit);
          changesMap.keySet().forEach(key -> {
            changesMap.put(key, newKind);
          });
        }
      }
    }

    @Override
    public @Nullable EdgeData<FilePath> findRename(int parent, int child, @NotNull FilePath path, boolean isChildPath) {
      if (path.isDirectory()) return findFolderRename(parent, child, path, isChildPath);
      return super.findRename(parent, child, path, isChildPath);
    }

    private @Nullable EdgeData<FilePath> findFolderRename(int parent, int child, @NotNull FilePath path, boolean isChildPath) {
      EdgeData<FilePath> rename = renamesMap.get(new EdgeData<>(parent, child));
      if (rename == null) return null;
      return FILE_PATH_HASHING_STRATEGY.equals(isChildPath ? rename.child : rename.parent, path) ? rename : null;
    }
  }

  //
  // Util
  //

  public @NotNull VcsLogStorage getLogStorage() {
    return myLogStorage;
  }

  @NotNull VcsLogStorageBackend getIndexStorageBackend() {
    return myIndexStorageBackend;
  }

  private @Nullable VirtualFile getRoot(@NotNull FilePath path) {
    if (myIsProjectLog) return VcsLogUtil.getActualRoot(myProject, path);
    return VcsLogUtil.getActualRoot(myProject, myProviders, path);
  }

  private void executeAndCatch(@NotNull Throwable2Runnable<IOException, StorageException> runnable) {
    executeAndCatch(() -> {
      runnable.run();
      return null;
    }, null);
  }

  private @Nullable <T> T executeAndCatch(@NotNull Throwable2Computable<T, IOException, StorageException> computable) {
    return executeAndCatch(computable, null);
  }

  @Contract("_, !null -> !null")
  private @Nullable <T> T executeAndCatch(@NotNull Throwable2Computable<? extends T, IOException, StorageException> computable,
                                          @Nullable T defaultValue) {
    try {
      return computable.compute();
    }
    catch (IOException | UncheckedIOException | StorageException | CorruptedDataException e) {
      myIndexStorageBackend.markCorrupted();
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
    }
    catch (RuntimeException e) {
      processRuntimeException(e);
    }
    return defaultValue;
  }

  private void processRuntimeException(@NotNull RuntimeException e) {
    if (e instanceof ProcessCanceledException) throw e;

    if (e.getCause() instanceof IOException || e.getCause() instanceof StorageException) {
      myIndexStorageBackend.markCorrupted();
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Index, e);
    }
    else {
      LOG.error("Unknown exception in Vcs Log index processing", e);
      throw new RuntimeException(e);
    }
  }

  private static final class CorruptedDataException extends RuntimeException {
    CorruptedDataException(@NotNull String message) {
      super(message);
    }
  }

  @FunctionalInterface
  private interface Throwable2Runnable<E1 extends Throwable, E2 extends Throwable> {
    void run() throws E1, E2;
  }
}
