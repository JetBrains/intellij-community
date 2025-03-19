// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class CompositeChangeListFilteringStrategy implements ChangeListFilteringStrategy {
  private final TreeMap<CommittedChangesFilterKey, ChangeListFilteringStrategy> myDelegates;
  private boolean myInSetBase;

  public CompositeChangeListFilteringStrategy() {
    myDelegates = new TreeMap<>();
    myInSetBase = false;
  }

  @Override
  public JComponent getFilterUI() {
    return null;
  }

  @Override
  public @NotNull CommittedChangesFilterKey getKey() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    setFilterBaseImpl(changeLists, true);
  }

  private List<CommittedChangeList> setFilterBaseImpl(final List<? extends CommittedChangeList> changeLists, final boolean setFirst) {
    List<CommittedChangeList> list = new ArrayList<>(changeLists);
    if (myInSetBase) {
      return list;
    }
    myInSetBase = true;

    boolean callSetFilterBase = setFirst;
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      if (callSetFilterBase) {
        delegate.setFilterBase(list);
      }
      callSetFilterBase = true;
      list = delegate.filterChangeLists(list);
    }
    myInSetBase = false;
    return list;
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener) {
    // not used
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.addChangeListener(listener);
    }
  }

  @Override
  public void removeChangeListener(@NotNull ChangeListener listener) {
    // not used
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.removeChangeListener(listener);
    }
  }

  @Override
  public void resetFilterBase() {
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.resetFilterBase();
    }
  }

  @Override
  public void appendFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    List<CommittedChangeList> list = new ArrayList<>(changeLists);
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.appendFilterBase(list);
      list = delegate.filterChangeLists(list);
    }
  }

  @Override
  public @NotNull List<CommittedChangeList> filterChangeLists(@NotNull List<? extends CommittedChangeList> changeLists) {
    return setFilterBaseImpl(changeLists, false);
  }

  public void addStrategy(final CommittedChangesFilterKey key, final ChangeListFilteringStrategy strategy) {
    myDelegates.put(key, strategy);
  }

  public ChangeListFilteringStrategy removeStrategy(final CommittedChangesFilterKey key) {
    return myDelegates.remove(key);
  }
}
