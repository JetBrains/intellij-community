package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public abstract class ChangeListManagerEx extends ChangeListManager {
  @Nullable
  public abstract LocalChangeList getIdentityChangeList(Change change);
  public abstract boolean isInUpdate();
  public abstract Collection<LocalChangeList> getInvolvedListsFilterChanges(final Collection<Change> changes, final List<Change> validChanges);
}