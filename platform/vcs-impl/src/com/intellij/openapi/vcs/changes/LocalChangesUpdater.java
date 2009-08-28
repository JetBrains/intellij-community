package com.intellij.openapi.vcs.changes;

import com.intellij.lifecycle.AtomicSectionsAware;

public interface LocalChangesUpdater {
  void execute(boolean updateUnversioned, AtomicSectionsAware atomicSectionsAware);
}
