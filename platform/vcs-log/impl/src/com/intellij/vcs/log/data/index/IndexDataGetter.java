// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.PersistentMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.history.EdgeData;
import com.intellij.vcs.log.history.FileHistoryData;
import com.intellij.vcs.log.history.VcsDirectoryRenamesProvider;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.TroveUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogMultiplePatternsTextFilter;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.intellij.vcs.log.history.FileHistoryKt.FILE_PATH_HASHING_STRATEGY;

public final class IndexDataGetter {
  @NotNull private final Project myProject;
  @NotNull private final Set<? extends VirtualFile> myRoots;
  @NotNull private final VcsLogPersistentIndex.IndexStorage myIndexStorage;
  @NotNull private final VcsLogStorage myLogStorage;
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;
  @NotNull private final VcsDirectoryRenamesProvider myDirectoryRenamesProvider;

  public IndexDataGetter(@NotNull Project project,
                         @NotNull Set<? extends VirtualFile> roots,
                         @NotNull VcsLogPersistentIndex.IndexStorage indexStorage,
                         @NotNull VcsLogStorage logStorage,
                         @NotNull FatalErrorHandler fatalErrorsConsumer) {
    myProject = project;
    myRoots = roots;
    myIndexStorage = indexStorage;
    myLogStorage = logStorage;
    myFatalErrorsConsumer = fatalErrorsConsumer;

    myDirectoryRenamesProvider = VcsDirectoryRenamesProvider.getInstance(myProject);
  }

  //
  // Getters from forward index
  //

  @Nullable
  public VcsUser getAuthor(int commit) {
    return executeAndCatch(() -> myIndexStorage.users.getAuthorForCommit(commit));
  }

  @Nullable
  public VcsUser getCommitter(int commit) {
    return executeAndCatch(() -> {
      Integer committer = myIndexStorage.committers.get(commit);
      if (committer != null) {
        return myIndexStorage.users.getUserById(committer);
      }
      if (myIndexStorage.commits.contains(commit)) {
        return myIndexStorage.users.getAuthorForCommit(commit);
      }
      return null;
    });
  }

  @Nullable
  public Long getAuthorTime(int commit) {
    return executeAndCatch(() -> {
      Pair<Long, Long> time = myIndexStorage.timestamps.get(commit);
      if (time == null) return null;
      return time.first;
    });
  }

  @Nullable
  public Long getCommitTime(int commit) {
    return executeAndCatch(() -> {
      Pair<Long, Long> time = myIndexStorage.timestamps.get(commit);
      if (time == null) return null;
      return time.second;
    });
  }

  @Nullable
  public String getFullMessage(int index) {
    return executeAndCatch(() -> myIndexStorage.messages.get(index));
  }

  @Nullable
  public List<Hash> getParents(int index) {
    return executeAndCatch(() -> {
      List<Integer> parentsIndexes = myIndexStorage.parents.get(index);
      if (parentsIndexes == null) return null;
      List<Hash> result = new ArrayList<>();
      for (int parentIndex : parentsIndexes) {
        CommitId id = myLogStorage.getCommitId(parentIndex);
        if (id == null) return null;
        result.add(id.getHash());
      }
      return result;
    });
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
        return ContainerUtil.find(files, file -> file.isDirectory() && myRoots.contains(file.getVirtualFile())) == null;
      }
      return false;
    });
  }

  @NotNull
  public Set<Integer> filter(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    return filter(detailsFilters, null);
  }

  @NotNull
  public IntSet filter(@NotNull List<VcsLogDetailsFilter> detailsFilters, @Nullable IntSet candidates) {
    VcsLogTextFilter textFilter = ContainerUtil.findInstance(detailsFilters, VcsLogTextFilter.class);
    VcsLogUserFilter userFilter = ContainerUtil.findInstance(detailsFilters, VcsLogUserFilter.class);
    VcsLogStructureFilter pathFilter = ContainerUtil.findInstance(detailsFilters, VcsLogStructureFilter.class);

    IntSet filteredByUser = null;
    if (userFilter != null) {
      Set<VcsUser> users = new HashSet<>();
      for (VirtualFile root : myRoots) {
        users.addAll(userFilter.getUsers(root));
      }

      filteredByUser = filterUsers(users);
    }

    IntSet filteredByPath = null;
    if (pathFilter != null) {
      filteredByPath = filterPaths(pathFilter.getFiles());
    }

    IntSet filteredByUserAndPath = TroveUtil.intersect(filteredByUser, filteredByPath, candidates);
    if (textFilter == null) {
      return filteredByUserAndPath == null ? IntSets.EMPTY_SET : filteredByUserAndPath;
    }
    return filterMessages(textFilter, filteredByUserAndPath);
  }

  @NotNull
  private IntSet filterUsers(@NotNull Set<? extends VcsUser> users) {
    return executeAndCatch(() -> myIndexStorage.users.getCommitsForUsers(users), new IntOpenHashSet());
  }

  @NotNull
  private IntSet filterPaths(@NotNull Collection<? extends FilePath> paths) {
    return executeAndCatch(() -> {
      IntSet result = new IntOpenHashSet();
      for (FilePath path : paths) {
        result.addAll(createFileHistoryData(path).build().getCommits());
      }
      return result;
    }, new IntOpenHashSet());
  }

  @NotNull
  private IntSet filterMessages(@NotNull VcsLogTextFilter filter, @Nullable IntIterable candidates) {
    if (!filter.isRegex() || filter instanceof VcsLogMultiplePatternsTextFilter) {
      IntSet resultByTrigrams = executeAndCatch(() -> {
        List<String> trigramSources = filter instanceof VcsLogMultiplePatternsTextFilter ?
                                      ((VcsLogMultiplePatternsTextFilter)filter).getPatterns() :
                                      Collections.singletonList(filter.getText());
        IntCollection commitsForSearch = new IntOpenHashSet();
        for (String string : trigramSources) {
          IntSet commits = myIndexStorage.trigrams.getCommitsForSubstring(string);
          if (commits == null) {
            return null;
          }

          if (candidates == null) {
            commitsForSearch.addAll(commits);
          }
          else {
            for (IntIterator iterator = candidates.iterator(); iterator.hasNext(); ) {
              int v = iterator.nextInt();
              if (commits.contains(v)) {
                commitsForSearch.add(v);
              }
            }
          }
        }
        IntSet result = new IntOpenHashSet();
        for (IntIterator iterator = commitsForSearch.iterator(); iterator.hasNext(); ) {
          int commit = iterator.nextInt();
          try {
            String value = myIndexStorage.messages.get(commit);
            if (value != null && filter.matches(value)) {
              result.add(commit);
            }
          }
          catch (IOException e) {
            myFatalErrorsConsumer.consume(this, e);
            break;
          }
        }
        return result;
      });

      if (resultByTrigrams != null) {
        return resultByTrigrams;
      }
    }

    return filter(myIndexStorage.messages, candidates, filter::matches);
  }

  @NotNull
  private <T> IntSet filter(@NotNull PersistentMap<Integer, T> map, @Nullable IntIterable candidates,
                            @NotNull Condition<? super T> condition) {
    IntSet result = new IntOpenHashSet();
    if (candidates == null) {
      return executeAndCatch(() -> {
        processKeys(map, commit -> filterCommit(map, commit, condition, result));
        return result;
      }, result);
    }

    for (IntIterator iterator = candidates.iterator(); iterator.hasNext(); ) {
      if (!filterCommit(map, iterator.nextInt(), condition, result)) {
        break;
      }
    }
    return result;
  }

  private <T> boolean filterCommit(@NotNull PersistentMap<Integer, T> map, int commit,
                                   @NotNull Condition<? super T> condition, @NotNull IntSet result) {
    try {
      T value = map.get(commit);
      if (value != null) {
        if (condition.value(value)) {
          result.add(commit);
        }
      }
    }
    catch (IOException e) {
      myFatalErrorsConsumer.consume(this, e);
      return false;
    }
    return true;
  }

  //
  // File history
  //

  @NotNull
  private TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
    TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> affectedCommits = new TIntObjectHashMap<>();

    VirtualFile root = VcsLogUtil.getActualRoot(myProject, path);
    if (myRoots.contains(root) && root != null) {
      executeAndCatch(() -> {
        myIndexStorage.paths.iterateCommits(root, path, (changes, commit) -> executeAndCatch(() -> {
          List<Integer> parents = myIndexStorage.parents.get(commit);
          if (parents == null) {
            throw new CorruptedDataException("No parents for commit " + commit);
          }

          TIntObjectHashMap<VcsLogPathsIndex.ChangeKind> changesMap = new TIntObjectHashMap<>();
          if (parents.size() == 0 && !changes.isEmpty()) {
            changesMap.put(commit, ContainerUtil.getFirstItem(changes));
          }
          else {
            if (parents.size() != changes.size()) {
              throw new CorruptedDataException("Commit " + commit + " has " + parents.size() +
                                               " parents, but " + changes.size() + " changes.");
            }
            for (Pair<Integer, VcsLogPathsIndex.ChangeKind> parentAndChanges : ContainerUtil.zip(parents, changes)) {
              changesMap.put(parentAndChanges.first, parentAndChanges.second);
            }
          }

          affectedCommits.put(commit, changesMap);

          return null;
        }));
        return null;
      });
    }
    return affectedCommits;
  }

  @NotNull
  public FileHistoryData createFileHistoryData(@NotNull FilePath path) {
    return createFileHistoryData(Collections.singletonList(path));
  }

  @NotNull
  public FileHistoryData createFileHistoryData(@NotNull Collection<? extends FilePath> paths) {
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

    @NotNull
    @Override
    public TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
      return IndexDataGetter.this.getAffectedCommits(path);
    }

    @Nullable
    @Override
    public EdgeData<FilePath> findRename(int parent, int child, @NotNull FilePath path, boolean isChildPath) {
      VirtualFile root = Objects.requireNonNull(VcsLogUtil.getActualRoot(myProject, path));
      return executeAndCatch(() -> {
        return myIndexStorage.paths.findRename(parent, child, root, path, isChildPath);
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
        if (VcsFileUtil.isAncestor(rename.getChild(), startPath, false)) {
          FilePath renamedPath = VcsUtil.getFilePath(rename.getParent().getPath() + "/" +
                                                     VcsFileUtil.relativePath(rename.getChild(), startPath), true);
          renamesMap.put(new EdgeData<>(myLogStorage.getCommitIndex(commits.getParent().getHash(), commits.getParent().getRoot()),
                                        myLogStorage.getCommitIndex(commits.getChild().getHash(), commits.getChild().getRoot())),
                         new EdgeData<>(renamedPath, startPath));
        }
      }
    }

    @NotNull
    @Override
    public TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
      TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> affectedCommits = super.getAffectedCommits(path);
      if (!path.isDirectory()) return affectedCommits;
      hackAffectedCommits(path, affectedCommits);
      return affectedCommits;
    }

    private void hackAffectedCommits(@NotNull FilePath path,
                                     @NotNull TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> affectedCommits) {
      for (Map.Entry<EdgeData<Integer>, EdgeData<FilePath>> entry : renamesMap.entrySet()) {
        int childCommit = entry.getKey().getChild();
        if (affectedCommits.containsKey(childCommit)) {
          EdgeData<FilePath> rename = entry.getValue();
          if (FILE_PATH_HASHING_STRATEGY.equals(rename.getChild(), path)) {
            affectedCommits.get(childCommit).transformValues(value -> VcsLogPathsIndex.ChangeKind.ADDED);
          }
          else if (FILE_PATH_HASHING_STRATEGY.equals(rename.getParent(), path)) {
            affectedCommits.get(childCommit).transformValues(value -> VcsLogPathsIndex.ChangeKind.REMOVED);
          }
        }
      }
    }

    @Nullable
    @Override
    public EdgeData<FilePath> findRename(int parent, int child, @NotNull FilePath path, boolean isChildPath) {
      if (path.isDirectory()) return findFolderRename(parent, child, path, isChildPath);
      return super.findRename(parent, child, path, isChildPath);
    }

    @Nullable
    private EdgeData<FilePath> findFolderRename(int parent, int child, @NotNull FilePath path, boolean isChildPath) {
      EdgeData<FilePath> rename = renamesMap.get(new EdgeData<>(parent, child));
      if (rename == null) return null;
      return FILE_PATH_HASHING_STRATEGY.equals(isChildPath ? rename.getChild() : rename.getParent(), path) ? rename : null;
    }
  }

  //
  // Util
  //

  @NotNull
  public VcsLogStorage getLogStorage() {
    return myLogStorage;
  }

  private static <T> void processKeys(@NotNull PersistentMap<Integer, T> map, @NotNull Processor<Integer> processor) throws IOException {
    if (map instanceof PersistentHashMap) {
      ((PersistentHashMap<Integer, T>)map).processKeysWithExistingMapping(processor);
    }
    else {
      map.processKeys(processor);
    }
  }

  @Nullable
  private <T> T executeAndCatch(@NotNull Throwable2Computable<T, IOException, StorageException> computable) {
    return executeAndCatch(computable, null);
  }

  @Contract("_, !null -> !null")
  @Nullable
  private <T> T executeAndCatch(@NotNull Throwable2Computable<? extends T, IOException, StorageException> computable,
                                @Nullable T defaultValue) {
    try {
      return computable.compute();
    }
    catch (IOException | StorageException | CorruptedDataException e) {
      myIndexStorage.markCorrupted();
      myFatalErrorsConsumer.consume(this, e);
    }
    catch (RuntimeException e) {
      processRuntimeException(e);
    }
    return defaultValue;
  }

  private void processRuntimeException(@NotNull RuntimeException e) {
    if (e instanceof ProcessCanceledException) throw e;
    myIndexStorage.markCorrupted();
    if (e.getCause() instanceof IOException || e.getCause() instanceof StorageException) {
      myFatalErrorsConsumer.consume(this, e);
    }
    else {
      throw new RuntimeException(e);
    }
  }

  private static class CorruptedDataException extends RuntimeException {
    CorruptedDataException(@NotNull String message) {
      super(message);
    }
  }
}
