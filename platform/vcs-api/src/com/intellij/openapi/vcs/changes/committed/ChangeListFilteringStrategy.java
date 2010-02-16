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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.List;

/**
 * @author yole
 */
public interface ChangeListFilteringStrategy {
  @Nullable
  JComponent getFilterUI();
  void setFilterBase(List<CommittedChangeList> changeLists);
  void addChangeListener(ChangeListener listener);
  void removeChangeListener(ChangeListener listener);

  @Nullable
  void resetFilterBase();
  void appendFilterBase(List<CommittedChangeList> changeLists);

  @NotNull
  List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists);

  ChangeListFilteringStrategy NONE = new ChangeListFilteringStrategy() {
    public String toString() {
      return "None";
    }

    @Nullable
    public JComponent getFilterUI() {
      return null;
    }

    public void setFilterBase(List<CommittedChangeList> changeLists) {
    }

    public void addChangeListener(ChangeListener listener) {
    }

    public void removeChangeListener(ChangeListener listener) {
    }

    @Nullable
    public void resetFilterBase() {
    }

    public void appendFilterBase(List<CommittedChangeList> changeLists) {
    }

    @NotNull
    public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
      return changeLists;
    }
  };
}
