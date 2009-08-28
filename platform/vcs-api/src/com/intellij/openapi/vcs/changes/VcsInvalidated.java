package com.intellij.openapi.vcs.changes;

import java.util.List;

public class VcsInvalidated {
  private final List<VcsDirtyScope> myScopes;
  private final boolean myEverythingDirty;

  public VcsInvalidated(final List<VcsDirtyScope> scopes, final boolean everythingDirty) {
    myScopes = scopes;
    myEverythingDirty = everythingDirty;
  }

  public List<VcsDirtyScope> getScopes() {
    return myScopes;
  }

  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  public boolean isEmpty() {
    return (! myEverythingDirty) && myScopes.isEmpty();
  }
}
