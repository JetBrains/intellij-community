package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsListener;

import java.util.Collection;

public interface ChangesOnServerTracker extends PlusMinus<Pair<String, AbstractVcs>>, VcsListener {
  // todo add vcs parameter???
  void invalidate(final Collection<String> paths);
  boolean isUpToDate(final Change change);
  boolean updateStep();
}
