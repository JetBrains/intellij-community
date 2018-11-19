// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;


import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.vcs.log.impl.CommonUiProperties.*;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.impl.CommonUiProperties.*;
import static com.intellij.vcs.log.ui.table.GraphTableModel.*;

@State(name = "Vcs.Log.History.Properties", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class FileHistoryUiProperties implements VcsLogUiProperties, PersistentStateComponent<FileHistoryUiProperties.State> {
  public static final VcsLogUiProperty<Boolean> SHOW_ALL_BRANCHES = new VcsLogUiProperty<>("Table.ShowOtherBranches");
  @NotNull private final Collection<PropertiesChangeListener> myListeners = ContainerUtil.newLinkedHashSet();
  private State myState = new State();

  public static class State {
    public boolean SHOW_DETAILS = false;
    public boolean SHOW_OTHER_BRANCHES = false;
    public Map<Integer, Integer> COLUMN_WIDTH = ContainerUtil.newHashMap();
    public List<Integer> COLUMN_ORDER = ContainerUtil.newArrayList();
    public boolean SHOW_DIFF_PREVIEW = true;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperty<T> property) {
    if (SHOW_DETAILS.equals(property)) {
      return (T)Boolean.valueOf(myState.SHOW_DETAILS);
    }
    else if (SHOW_ALL_BRANCHES.equals(property)) {
      return (T)Boolean.valueOf(myState.SHOW_OTHER_BRANCHES);
    }
    else if (COLUMN_ORDER.equals(property)) {
      List<Integer> order = myState.COLUMN_ORDER;
      if (order == null || order.isEmpty()) {
        order = ContainerUtilRt.newArrayList(ROOT_COLUMN, AUTHOR_COLUMN, DATE_COLUMN, COMMIT_COLUMN);
      }
      return (T)order;
    }
    else if (property instanceof TableColumnProperty) {
      Integer savedWidth = myState.COLUMN_WIDTH.get(((TableColumnProperty)property).getColumn());
      if (savedWidth == null) return (T)Integer.valueOf(-1);
      return (T)savedWidth;
    }
    else if (SHOW_DIFF_PREVIEW.equals(property)) {
      return (T)Boolean.valueOf(myState.SHOW_DIFF_PREVIEW);
    }
    throw new UnsupportedOperationException("Unknown property " + property);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value) {
    if (SHOW_DETAILS.equals(property)) {
      myState.SHOW_DETAILS = (Boolean)value;
    }
    else if (SHOW_ALL_BRANCHES.equals(property)) {
      myState.SHOW_OTHER_BRANCHES = (Boolean)value;
    }
    else if (COLUMN_ORDER.equals(property)) {
      myState.COLUMN_ORDER = (List<Integer>)value;
    }
    else if (property instanceof TableColumnProperty) {
      myState.COLUMN_WIDTH.put(((TableColumnProperty)property).getColumn(), (Integer)value);
    }
    else if (SHOW_DIFF_PREVIEW.equals(property)) {
      myState.SHOW_DIFF_PREVIEW = (Boolean)value;
    }
    else {
      throw new UnsupportedOperationException("Unknown property " + property);
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperty<T> property) {
    return SHOW_DETAILS.equals(property) ||
           SHOW_ALL_BRANCHES.equals(property) ||
           COLUMN_ORDER.equals(property) ||
           SHOW_DIFF_PREVIEW.equals(property) ||
           property instanceof TableColumnProperty;
  }

  @Override
  @Nullable
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @Override
  public void addChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.remove(listener);
  }
}
