package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import org.jetbrains.annotations.NotNull;

interface JpsOutputLoader {
  LoaderStatus load(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager indicatorManager);
  void rollback();
  void apply();

  enum LoaderStatus {
    COMPLETE, FAILED
  }
}