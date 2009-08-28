package com.intellij.openapi.vcs.changes;

public interface VcsDirtyMarker<T> {
  void addDirtyDirRecursively(final T newcomer);
  void addDirtyFile(final T newcomer);
}
