// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.VcsLogApplicationSettings;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.table.VcsLogColumnDeprecated;
import com.intellij.vcs.log.ui.table.column.Date;
import com.intellij.vcs.log.ui.table.column.*;
import org.jetbrains.annotations.ApiStatus;
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

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperty<T> property) {
    if (property instanceof TableColumnWidthProperty) {
      TableColumnWidthProperty tableColumnWidthProperty = (TableColumnWidthProperty)property;
      if (!myState.COLUMN_WIDTH.isEmpty()) {
        tableColumnWidthProperty.moveOldSettings(myState.COLUMN_WIDTH, myState.COLUMN_ID_WIDTH);
        myState.COLUMN_WIDTH = new HashMap<>();
      }
      Integer savedWidth = myState.COLUMN_ID_WIDTH.get(property.getName());
      if (savedWidth == null) {
        return (T)Integer.valueOf(-1);
      }
      return (T)savedWidth;
    }
    if (property instanceof TableColumnVisibilityProperty) {
      TableColumnVisibilityProperty visibilityProperty = (TableColumnVisibilityProperty)property;
      Boolean isVisible = myState.COLUMN_ID_VISIBILITY.get(visibilityProperty.getName());
      if (isVisible != null) {
        return (T)isVisible;
      }

      // visibility is not set, so we will get it from current/default order
      // otherwise column will be visible but not exist in order
      VcsLogColumn<?> column = visibilityProperty.getColumn();
      if (get(COLUMN_ID_ORDER).contains(column.getId())) {
        return (T)Boolean.TRUE;
      }
      if (column instanceof VcsLogCustomColumn) {
        return (T)Boolean.valueOf(((VcsLogCustomColumn<?>)column).isEnabledByDefault());
      }
      return (T)Boolean.FALSE;
    }
    return property.match()
      .ifEq(SHOW_DETAILS).then(myState.SHOW_DETAILS)
      .ifEq(SHOW_ALL_BRANCHES).then(myState.SHOW_OTHER_BRANCHES)
      .ifEq(SHOW_DIFF_PREVIEW).then(myState.SHOW_DIFF_PREVIEW)
      .ifEq(SHOW_ROOT_NAMES).then(myState.SHOW_ROOT_NAMES)
      .ifEq(PREFER_COMMIT_DATE).thenGet(() -> myAppSettings.get(PREFER_COMMIT_DATE))
      .ifEq(COLUMN_ID_ORDER).thenGet(() -> {
        List<String> order = myState.COLUMN_ID_ORDER;
        if (order != null && !order.isEmpty()) {
          return order;
        }
        List<Integer> oldOrder = myState.COLUMN_ORDER;
        if (oldOrder != null && !oldOrder.isEmpty()) {
          List<String> oldIdOrder = ContainerUtil.map(oldOrder, it -> VcsLogColumnDeprecated.getVcsLogColumnEx(it).getId());
          myState.COLUMN_ID_ORDER = oldIdOrder;
          myState.COLUMN_ORDER = new ArrayList<>();
          return oldIdOrder;
        }
        return ContainerUtil.map(Arrays.asList(Root.INSTANCE, Author.INSTANCE, Date.INSTANCE, Commit.INSTANCE), VcsLogColumn::getId);
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
    else if (COLUMN_ID_ORDER.equals(property)) {
      myState.COLUMN_ID_ORDER = (List<String>)(value);
    }
    else if (property instanceof TableColumnWidthProperty) {
      myState.COLUMN_ID_WIDTH.put(property.getName(), (Integer)value);
    }
    else if (property instanceof TableColumnVisibilityProperty) {
      myState.COLUMN_ID_VISIBILITY.put(property.getName(), (Boolean)value);
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
           COLUMN_ID_ORDER.equals(property) ||
           SHOW_DIFF_PREVIEW.equals(property) ||
           SHOW_ROOT_NAMES.equals(property) ||
           PREFER_COMMIT_DATE.equals(property) ||
           property instanceof TableColumnWidthProperty ||
           property instanceof TableColumnVisibilityProperty;
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

  public static class State {
    public boolean SHOW_DETAILS = false;
    public boolean SHOW_OTHER_BRANCHES = false;
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public Map<Integer, Integer> COLUMN_WIDTH = new HashMap<>();
    public Map<String, Integer> COLUMN_ID_WIDTH = new HashMap<>();
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
    public List<Integer> COLUMN_ORDER = new ArrayList<>();
    public List<String> COLUMN_ID_ORDER = new ArrayList<>();
    public Map<String, Boolean> COLUMN_ID_VISIBILITY = new HashMap<>();
    public boolean SHOW_DIFF_PREVIEW = true;
    public boolean SHOW_ROOT_NAMES = false;
  }
}
