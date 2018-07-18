package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogObjectsFactory;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.IndexedDetails;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.util.TroveUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MiniDetailsGetter extends AbstractDataGetter<VcsShortCommitDetails> {

  @NotNull private final TopCommitsCache myTopCommitsDetailsCache;
  @NotNull private final VcsLogObjectsFactory myFactory;

  MiniDetailsGetter(@NotNull Project project,
                    @NotNull VcsLogStorage storage,
                    @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                    @NotNull TopCommitsCache topCommitsDetailsCache,
                    @NotNull VcsLogIndex index,
                    @NotNull Disposable parentDisposable) {
    super(storage, logProviders, new VcsCommitCache<>(), index, parentDisposable);
    myTopCommitsDetailsCache = topCommitsDetailsCache;
    myFactory = ServiceManager.getService(project, VcsLogObjectsFactory.class);
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getFromAdditionalCache(int commitId) {
    return myTopCommitsDetailsCache.get(commitId);
  }

  @NotNull
  @Override
  protected List<? extends VcsShortCommitDetails> readDetails(@NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                                                              @NotNull List<String> hashes) throws VcsException {
    return logProvider.readShortDetails(root, hashes);
  }

  @NotNull
  @Override
  public TIntObjectHashMap<VcsShortCommitDetails> preLoadCommitData(@NotNull TIntHashSet commits) throws VcsException {
    IndexDataGetter dataGetter = myIndex.getDataGetter();
    if (dataGetter == null) return super.preLoadCommitData(commits);

    TIntHashSet notIndexed = new TIntHashSet();

    TIntObjectHashMap<VcsShortCommitDetails> result = TroveUtil.map2MapNotNull(commits, commit -> {
      VcsCommitMetadata metadata = IndexedDetails.createMetadata(commit, dataGetter, myStorage, myFactory);
      if (metadata == null) notIndexed.add(commit);
      return metadata;
    });
    saveInCache(result);

    if (!notIndexed.isEmpty()) {
      TroveUtil.putAll(result, super.preLoadCommitData(notIndexed));
    }
    return result;
  }
}
