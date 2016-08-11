/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public JComponent getFilterUI() {
    return null;
  }

  @Override
  public CommittedChangesFilterKey getKey() {
    throw new UnsupportedOperationException();
  }

  public void setFilterBase(final List<CommittedChangeList> changeLists) {
    setFilterBaseImpl(changeLists, true);
  }

  private List<CommittedChangeList> setFilterBaseImpl(final List<CommittedChangeList> changeLists, final boolean setFirst) {
    if (myInSetBase) {
      return changeLists;
    }
    myInSetBase = true;

    List<CommittedChangeList> list = new ArrayList<>(changeLists);
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

  public void addChangeListener(final ChangeListener listener) {
    // not used
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.addChangeListener(listener);
    }
  }

  public void removeChangeListener(final ChangeListener listener) {
    // not used
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.removeChangeListener(listener);
    }
  }

  public void resetFilterBase() {
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.resetFilterBase();
    }
  }

  public void appendFilterBase(final List<CommittedChangeList> changeLists) {
    List<CommittedChangeList> list = new ArrayList<>(changeLists);
    for (final ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.appendFilterBase(list);
      list = delegate.filterChangeLists(list);
    }
  }

  @NotNull
  public List<CommittedChangeList> filterChangeLists(final List<CommittedChangeList> changeLists) {
    return setFilterBaseImpl(changeLists, false);
  }

  public void addStrategy(final CommittedChangesFilterKey key, final ChangeListFilteringStrategy strategy) {
    myDelegates.put(key, strategy);
  }

  public ChangeListFilteringStrategy removeStrategy(final CommittedChangesFilterKey key) {
    return myDelegates.remove(key);
  }
}
