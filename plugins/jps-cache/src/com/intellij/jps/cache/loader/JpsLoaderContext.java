package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JpsLoaderContext {
  private final String commitId;
  private final SegmentedProgressIndicatorManager indicatorManager;
  private final SourcesState commitSourcesState;
  private final SourcesState currentSourcesState;

  private JpsLoaderContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager indicatorManager,
                           @NotNull SourcesState commitSourcesState, @Nullable SourcesState currentSourcesState) {
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
  SourcesState getCommitSourcesState() {
    return commitSourcesState;
  }

  @Nullable
  SourcesState getCurrentSourcesState() {
    return currentSourcesState;
  }

  static JpsLoaderContext createNewContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager indicatorManager,
                                           @NotNull SourcesState commitSourcesState, @Nullable SourcesState currentSourcesState) {
    return new JpsLoaderContext(commitId, indicatorManager, commitSourcesState, currentSourcesState);
  }
}
