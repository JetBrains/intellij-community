package com.intellij.vcs.log.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.GraphFacade;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.printer.idea.ColorGenerator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DataPack implements VcsLogDataPack {

  @NotNull private final RefsModel myRefsModel;
  @NotNull private final GraphFacade myGraphFacade;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;

  @NotNull
  public static DataPack build(@NotNull List<? extends GraphCommit<Integer>> commits,
                               @NotNull Collection<VcsRef> allRefs,
                               @NotNull ProgressIndicator indicator,
                               @NotNull NotNullFunction<Hash, Integer> indexGetter,
                               @NotNull NotNullFunction<Integer, Hash> hashGetter, 
                               @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    indicator.setText("Building graph...");
    final RefsModel refsModel = new RefsModel(allRefs, indexGetter);
    GraphColorManagerImpl colorManager = new GraphColorManagerImpl(refsModel, hashGetter, getRefManagerMap(providers));
    if (!commits.isEmpty()) {
      Set<Integer> branches = getBranchCommitHashIndexes(allRefs, indexGetter);
      PermanentGraphImpl<Integer> permanentGraph = PermanentGraphImpl.newInstance(commits, colorManager, branches);
      ColorGenerator colorGenerator = new ColorGenerator() {
        @Override
        public Color getColor(int colorId) {
          return com.intellij.vcs.log.graph.ColorGenerator.getColor(colorId);
        }
      };
      return new DataPack(refsModel, new DelegateGraphFacade(permanentGraph, colorGenerator), providers);
    }
    else {
      return new DataPack(refsModel, new EmptyGraphFacade(), providers);
    }
  }

  @NotNull
  private static Set<Integer> getBranchCommitHashIndexes(@NotNull Collection<VcsRef> allRefs,
                                                         @NotNull NotNullFunction<Hash, Integer> indexGetter) {
    Set<Integer> result = new HashSet<Integer>();
    for (VcsRef vcsRef : allRefs) {
      if (vcsRef.getType().isBranch())
        result.add(indexGetter.fun(vcsRef.getCommitHash()));
    }
    return result;
  }

  @NotNull
  private static Map<VirtualFile, VcsLogRefManager> getRefManagerMap(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    Map<VirtualFile, VcsLogRefManager> map = ContainerUtil.newHashMap();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      map.put(entry.getKey(), entry.getValue().getReferenceManager());
    }
    return map;
  }

  private DataPack(@NotNull RefsModel refsModel, @NotNull GraphFacade graphFacade, @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    myRefsModel = refsModel;
    myGraphFacade = graphFacade;
    myLogProviders = providers;
  }

  @NotNull
  public GraphFacade getGraphFacade() {
    return myGraphFacade;
  }

  @NotNull
  @Override
  public VcsLogRefs getRefs() {
    return myRefsModel;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

  @NotNull
  @Override
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogProviders;
  }

}
