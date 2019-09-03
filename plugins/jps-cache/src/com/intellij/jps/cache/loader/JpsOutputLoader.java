package com.intellij.jps.cache.loader;

import org.jetbrains.annotations.NotNull;

interface JpsOutputLoader {
  void load(@NotNull String commitId);
  void rollback();
  void apply();
}