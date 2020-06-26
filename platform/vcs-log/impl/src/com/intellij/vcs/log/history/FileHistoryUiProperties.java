// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.impl.CommonUiProperties.*;

@State(name = "Vcs.Log.History.Properties", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class FileHistoryUiProperties implements VcsLogUiProperties, PersistentStateComponent<FileHistoryUiProperties.State> {
  public static final VcsLogUiProperty<Boolean> SHOW_ALL_BRANCHES = new VcsLogUiProperty<>("Table.ShowOtherBranches");

  @NotNull private final Collection<PropertiesChangeListener> myListeners = new LinkedHashSet<>();
  @NotNull private final VcsLogApplicationSettings myAppSettings =
    ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);
  @NotNull private final PropertiesChangeListener myApplicationSettingsListener = this::onApplicationSettingChange;

  private State myState = new State();

  public static class State {
    public boolean SHOW_DETAILS = false;
    public boolean SHOW_OTHER_BRANCHES = false;
    public Map<Integer, Integer> COLUMN_WIDTH = new HashMap<>();
    public List<Integer> COLUMN_ORDER = new ArrayList<>();
    public boolean SHOW_DIFF_PREVIEW = true;
    public boolean SHOW_ROOT_NAMES = false;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperty<T> property) {
    if (property instanceof TableColumnProperty) {
      Integer savedWidth = myState.COLUMN_WIDTH.get(((TableColumnProperty)property).getColumnIndex());
      if (savedWidth == null) return (T)Integer.valueOf(-1);
      return (T)savedWidth;
    }
    return property.match()
      .ifEq(SHOW_DETAILS).then(myState.SHOW_DETAILS)
      .ifEq(SHOW_ALL_BRANCHES).then(myState.SHOW_OTHER_BRANCHES)
      .ifEq(SHOW_DIFF_PREVIEW).then(myState.SHOW_DIFF_PREVIEW)
      .ifEq(SHOW_ROOT_NAMES).then(myState.SHOW_ROOT_NAMES)
      .ifEq(PREFER_COMMIT_DATE).thenGet(() -> myAppSettings.get(PREFER_COMMIT_DATE))
      .ifEq(COLUMN_ORDER).thenGet(() -> {
        List<Integer> order = myState.COLUMN_ORDER;
        if (order == null || order.isEmpty()) {
          order = ContainerUtil.map(Arrays.asList(VcsLogColumn.ROOT, VcsLogColumn.AUTHOR, VcsLogColumn.DATE, VcsLogColumn.COMMIT),
                                    VcsLogColumn::ordinal);
        }
        return order;
      })
      .get();
  }

  private <T> void onApplicationSettingChange(@NotNull VcsLogUiProperty<T> property) {
    if (PREFER_COMMIT_DATE.equals(property)) {
      myListeners.forEach(l -> l.onPropertyChanged(property));
    }
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
      myState.COLUMN_WIDTH.put(((TableColumnProperty)property).getColumnIndex(), (Integer)value);
    }
    else if (SHOW_DIFF_PREVIEW.equals(property)) {
      myState.SHOW_DIFF_PREVIEW = (Boolean)value;
    }
    else if (SHOW_ROOT_NAMES.equals(property)) {
      myState.SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (PREFER_COMMIT_DATE.equals(property)) {
      myAppSettings.set(property, value);
      // listeners will be triggered via onApplicationSettingChange
      return;
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
           SHOW_ROOT_NAMES.equals(property) ||
           PREFER_COMMIT_DATE.equals(property) ||
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
    if (myListeners.isEmpty()) {
      myAppSettings.addChangeListener(myApplicationSettingsListener);
    }
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.remove(listener);
    if (myListeners.isEmpty()) {
      myAppSettings.removeChangeListener(myApplicationSettingsListener);
    }
  }
}
