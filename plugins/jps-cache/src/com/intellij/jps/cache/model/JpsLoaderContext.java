package com.intellij.jps.cache.model;

import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JpsLoaderContext {
  private final String commitId;
  private final SegmentedProgressIndicatorManager indicatorManager;
  private final Map<String, Map<String, BuildTargetState>> commitSourcesState;
  private final Map<String, Map<String, BuildTargetState>> currentSourcesState;

  private JpsLoaderContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager indicatorManager,
                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    this.commitId = commitId;
    this.indicatorManager = indicatorManager;
    this.commitSourcesState = commitSourcesState;
    this.currentSourcesState = currentSourcesState;
  }

  @NotNull
  public String getCommitId() {
    return commitId;
  }

  @NotNull
  public SegmentedProgressIndicatorManager getIndicatorManager() {
    return indicatorManager;
  }

  @NotNull
  public Map<String, Map<String, BuildTargetState>> getCommitSourcesState() {
    return commitSourcesState;
  }

  @Nullable
  public Map<String, Map<String, BuildTargetState>> getCurrentSourcesState() {
    return currentSourcesState;
  }

  public static JpsLoaderContext createNewContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager indicatorManager,
                                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return new JpsLoaderContext(commitId, indicatorManager, commitSourcesState, currentSourcesState);
  }
}
