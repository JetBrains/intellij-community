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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeChangeListFilteringStrategy implements ChangeListFilteringStrategy {
  private final Map<String, ChangeListFilteringStrategy> myDelegates;

  public CompositeChangeListFilteringStrategy() {
    myDelegates = new HashMap<String, ChangeListFilteringStrategy>();
  }

  public JComponent getFilterUI() {
    return null;
  }

  public void setFilterBase(final List<CommittedChangeList> changeLists) {
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.setFilterBase(changeLists);
    }
  }

  public void addChangeListener(final ChangeListener listener) {
    // not used
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.addChangeListener(listener);
    }
  }

  public void removeChangeListener(final ChangeListener listener) {
    // not used
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.removeChangeListener(listener);
    }
  }

  public void resetFilterBase() {
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.resetFilterBase();
    }
  }

  public void appendFilterBase(List<CommittedChangeList> changeLists) {
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      delegate.appendFilterBase(changeLists);
    }
  }

  @NotNull
  public List<CommittedChangeList> filterChangeLists(final List<CommittedChangeList> changeLists) {
    List<CommittedChangeList> result = new ArrayList<CommittedChangeList>(changeLists);
    for (ChangeListFilteringStrategy delegate : myDelegates.values()) {
      result = delegate.filterChangeLists(result);
    }
    return result;
  }

  public void addStrategy(final String key, final ChangeListFilteringStrategy strategy) {
    myDelegates.put(key, strategy);
  }

  public ChangeListFilteringStrategy removeStrategy(final String key) {
    return myDelegates.remove(key);
  }
}
