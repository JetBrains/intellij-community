package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class JpsLoaderContext {
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
  String getCommitId() {
    return commitId;
  }

  @NotNull
  SegmentedProgressIndicatorManager getIndicatorManager() {
    return indicatorManager;
  }

  @NotNull
  Map<String, Map<String, BuildTargetState>> getCommitSourcesState() {
    return commitSourcesState;
  }

  @Nullable
  Map<String, Map<String, BuildTargetState>> getCurrentSourcesState() {
    return currentSourcesState;
  }

  static JpsLoaderContext createNewContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager indicatorManager,
                                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return new JpsLoaderContext(commitId, indicatorManager, commitSourcesState, currentSourcesState);
  }
}
