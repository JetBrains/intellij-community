// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Throwable2Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.PersistentMap;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.history.FileNamesData;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.TroveUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogMultiplePatternsTextFilter;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IndexDataGetter {
  private static final Logger LOG = Logger.getInstance(IndexDataGetter.class);
  @NotNull private final Project myProject;
  @NotNull private final Set<? extends VirtualFile> myRoots;
  @NotNull private final VcsLogPersistentIndex.IndexStorage myIndexStorage;
  @NotNull private final VcsLogStorage myLogStorage;
  @NotNull private final FatalErrorHandler myFatalErrorsConsumer;

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
      List<Hash> result = ContainerUtil.newArrayList();
      for (int parentIndex : parentsIndexes) {
        CommitId id = myLogStorage.getCommitId(parentIndex);
        if (id == null) return null;
        result.add(id.getHash());
      }
      return result;
    });
  }

  @NotNull
  public Set<FilePath> getChangedPaths(int commit) {
    List<Hash> parents = getParents(commit);
    if (parents == null || parents.size() > 1) return Collections.emptySet();
    return getChangedPaths(commit, 0);
  }

  @NotNull
  public Set<FilePath> getChangedPaths(int commit, int parentIndex) {
    return executeAndCatch(() -> myIndexStorage.paths.getPathsChangedInCommit(commit, parentIndex), Collections.emptySet());
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
    VcsLogTextFilter textFilter = ContainerUtil.findInstance(detailsFilters, VcsLogTextFilter.class);
    VcsLogUserFilter userFilter = ContainerUtil.findInstance(detailsFilters, VcsLogUserFilter.class);
    VcsLogStructureFilter pathFilter = ContainerUtil.findInstance(detailsFilters, VcsLogStructureFilter.class);

    TIntHashSet filteredByMessage = null;
    if (textFilter != null) {
      filteredByMessage = filterMessages(textFilter);
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

  @NotNull
  private TIntHashSet filterUsers(@NotNull Set<? extends VcsUser> users) {
    return executeAndCatch(() -> myIndexStorage.users.getCommitsForUsers(users), new TIntHashSet());
  }

  @NotNull
  private TIntHashSet filterPaths(@NotNull Collection<? extends FilePath> paths) {
    return executeAndCatch(() -> {
      TIntHashSet result = new TIntHashSet();
      for (FilePath path : paths) {
        Set<Integer> commits = createFileNamesData(path).getCommits();
        if (commits.isEmpty() && !path.isDirectory()) {
          commits = createFileNamesData(VcsUtil.getFilePath(path.getPath(), true)).getCommits();
        }
        TroveUtil.addAll(result, commits);
      }
      return result;
    }, new TIntHashSet());
  }

  @NotNull
  private TIntHashSet filterMessages(@NotNull VcsLogTextFilter filter) {
    if (!filter.isRegex() || filter instanceof VcsLogMultiplePatternsTextFilter) {
      TIntHashSet resultByTrigrams = executeAndCatch(() -> {

        List<String> trigramSources = filter instanceof VcsLogMultiplePatternsTextFilter ?
                                      ((VcsLogMultiplePatternsTextFilter)filter).getPatterns() :
                                      Collections.singletonList(filter.getText());
        TIntHashSet commitsForSearch = new TIntHashSet();
        for (String string : trigramSources) {
          TIntHashSet commits = myIndexStorage.trigrams.getCommitsForSubstring(string);
          if (commits == null) return null;
          TroveUtil.addAll(commitsForSearch, commits);
        }
        TIntHashSet result = new TIntHashSet();
        commitsForSearch.forEach(commit -> {
          try {
            String value = myIndexStorage.messages.get(commit);
            if (value != null && filter.matches(value)) {
              result.add(commit);
            }
          }
          catch (IOException e) {
            myFatalErrorsConsumer.consume(this, e);
            return false;
          }
          return true;
        });
        return result;
      });

      if (resultByTrigrams != null) return resultByTrigrams;
    }

    return filter(myIndexStorage.messages, filter::matches);
  }

  @NotNull
  private <T> TIntHashSet filter(@NotNull PersistentMap<Integer, T> map, @NotNull Condition<? super T> condition) {
    TIntHashSet result = new TIntHashSet();
    return executeAndCatch(() -> {
      myIndexStorage.commits.process(commit -> {
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
      });
      return result;
    }, result);
  }

  //
  // File history
  //

  @SuppressWarnings("unused")
  @NotNull
  public Set<FilePath> getKnownNames(@NotNull FilePath path) {
    return executeAndCatch(() -> createFileNamesData(path).getFiles(), Collections.emptySet());
  }

  @NotNull
  public TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
    TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> affectedCommits = new TIntObjectHashMap<>();

    VirtualFile root = VcsLogUtil.getActualRoot(myProject, path);
    if (myRoots.contains(root)) {
      executeAndCatch(() -> {
        myIndexStorage.paths.iterateCommits(path, (changes, commit) -> executeAndCatch(() -> {
          List<Integer> parents = myIndexStorage.parents.get(commit);
          if (parents == null) {
            throw new CorruptedDataException("No parents for commit " + commit);
          }

          TIntObjectHashMap<VcsLogPathsIndex.ChangeKind> changesMap = new TIntObjectHashMap<>();
          if (parents.size() == 0 && !changes.isEmpty()) {
            changesMap.put(commit, ContainerUtil.getFirstItem(changes));
          }
          else {
            LOG.assertTrue(parents.size() == changes.size(),
                           "Commit " + commit + " has " + parents.size() + " parents, but " + changes.size() + " changes.");
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

  @Nullable
  public Couple<FilePath> findRename(int parent, int child, @NotNull BooleanFunction<? super Couple<FilePath>> accept) {
    return executeAndCatch(() -> myIndexStorage.paths.iterateRenames(parent, child, accept));
  }

  @NotNull
  public FileNamesData createFileNamesData(@NotNull FilePath path) {
    return createFileNamesData(Collections.singletonList(path));
  }

  @NotNull
  public FileNamesData createFileNamesData(@NotNull Collection<FilePath> paths) {
    return new FileNamesData(paths) {
      @NotNull
      @Override
      public TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>> getAffectedCommits(@NotNull FilePath path) {
        return IndexDataGetter.this.getAffectedCommits(path);
      }

      @Nullable
      @Override
      public Couple<FilePath> findRename(int parent, int child, @NotNull Function1<? super Couple<FilePath>, Boolean> accept) {
        return IndexDataGetter.this.findRename(parent, child, couple -> accept.invoke(couple));
      }
    };
  }

  //
  // Util
  //

  @NotNull
  public VcsLogStorage getLogStorage() {
    return myLogStorage;
  }

  @Nullable
  private <T> T executeAndCatch(@NotNull Throwable2Computable<T, IOException, StorageException> computable) {
    return executeAndCatch(computable, null);
  }

  @Contract("_, !null -> !null")
  @Nullable
  private <T> T executeAndCatch(@NotNull Throwable2Computable<? extends T, IOException, StorageException> computable, @Nullable T defaultValue) {
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
