package com.intellij.jps.cache.loader;

import com.intellij.jps.cache.model.JpsLoaderContext;
import org.jetbrains.annotations.NotNull;

interface JpsOutputLoader {
  LoaderStatus load(@NotNull JpsLoaderContext context);
  void rollback();
  void apply();

  enum LoaderStatus {
    COMPLETE, FAILED
  }
}