package com.intellij.jps.cache.loader;

import org.jetbrains.annotations.NotNull;

interface JpsOutputLoader {
  LoaderStatus load(@NotNull String commitId);
  void rollback();
  void apply();

  enum LoaderStatus {
    COMPLETE, FAILED
  }
}