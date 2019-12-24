package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.model.BuildTargetState;
import com.intellij.jps.cache.model.JpsLoaderContext;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

interface JpsOutputLoader<T> {
  T load(@NotNull JpsLoaderContext context);
  LoaderStatus extract(@Nullable Object loadResults, @NotNull SegmentedProgressIndicatorManager extractIndicatorManager);
  void rollback();
  void apply(@NotNull SegmentedProgressIndicatorManager indicatorManager);
  default int calculateDownloads(@NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                 @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return 1;
  }

  enum LoaderStatus {
    COMPLETE, FAILED
  }
}