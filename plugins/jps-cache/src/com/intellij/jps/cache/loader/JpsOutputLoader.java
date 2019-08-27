package com.intellij.jps.cache.loader;

public interface JpsOutputLoader {
  void load();
  void rollback();
  void apply();
}