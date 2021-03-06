// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.ui.table.VcsLogColumnDeprecated;
import com.intellij.vcs.log.ui.table.column.Date;
import com.intellij.vcs.log.ui.table.column.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.impl.CommonUiProperties.*;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;

@State(name = "Vcs.Log.App.Settings", storages = @Storage("vcs.xml"))
public class VcsLogApplicationSettings implements PersistentStateComponent<VcsLogApplicationSettings.State>, VcsLogUiProperties {
  @NotNull private final Set<VcsLogUiProperties.PropertiesChangeListener> myListeners = new LinkedHashSet<>();
  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperty<T> property) {
    if (property instanceof CustomBooleanProperty) {
      Boolean value = myState.CUSTOM_BOOLEAN_PROPERTIES.get(property.getName());
      if (value == null) {
        value = ((CustomBooleanProperty)property).defaultValue();
      }
      return (T)value;
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
      .ifEq(COMPACT_REFERENCES_VIEW).then(myState.COMPACT_REFERENCES_VIEW)
      .ifEq(SHOW_TAG_NAMES).then(myState.SHOW_TAG_NAMES)
      .ifEq(LABELS_LEFT_ALIGNED).then(myState.LABELS_LEFT_ALIGNED)
      .ifEq(SHOW_CHANGES_FROM_PARENTS).then(myState.SHOW_CHANGES_FROM_PARENTS)
      .ifEq(SHOW_DIFF_PREVIEW).then(myState.SHOW_DIFF_PREVIEW)
      .ifEq(DIFF_PREVIEW_VERTICAL_SPLIT).then(myState.DIFF_PREVIEW_VERTICAL_SPLIT)
      .ifEq(PREFER_COMMIT_DATE).then(myState.PREFER_COMMIT_DATE)
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
        return ContainerUtil.map(Arrays.asList(Root.INSTANCE, Commit.INSTANCE, Author.INSTANCE, Date.INSTANCE), VcsLogColumn::getId);
      })
      .get();
  }

  @Override
  public <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value) {
    if (property instanceof CustomBooleanProperty) {
      myState.CUSTOM_BOOLEAN_PROPERTIES.put(property.getName(), (Boolean)value);
    }
    else if (COMPACT_REFERENCES_VIEW.equals(property)) {
      myState.COMPACT_REFERENCES_VIEW = (Boolean)value;
    }
    else if (SHOW_TAG_NAMES.equals(property)) {
      myState.SHOW_TAG_NAMES = (Boolean)value;
    }
    else if (LABELS_LEFT_ALIGNED.equals(property)) {
      myState.LABELS_LEFT_ALIGNED = (Boolean)value;
    }
    else if (SHOW_CHANGES_FROM_PARENTS.equals(property)) {
      myState.SHOW_CHANGES_FROM_PARENTS = (Boolean)value;
    }
    else if (SHOW_DIFF_PREVIEW.equals(property)) {
      myState.SHOW_DIFF_PREVIEW = (Boolean)value;
    }
    else if (DIFF_PREVIEW_VERTICAL_SPLIT.equals(property)) {
      myState.DIFF_PREVIEW_VERTICAL_SPLIT = (Boolean)value;
    }
    else if (PREFER_COMMIT_DATE.equals(property)) {
      myState.PREFER_COMMIT_DATE = (Boolean)value;
    }
    else if (COLUMN_ID_ORDER.equals(property)) {
      //noinspection unchecked
      myState.COLUMN_ID_ORDER = (List<String>)value;
    }
    else if (property instanceof TableColumnVisibilityProperty) {
      myState.COLUMN_ID_VISIBILITY.put(property.getName(), (Boolean)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperty<T> property) {
    return property instanceof CustomBooleanProperty ||
           COMPACT_REFERENCES_VIEW.equals(property) || SHOW_TAG_NAMES.equals(property) || LABELS_LEFT_ALIGNED.equals(property) ||
           SHOW_DIFF_PREVIEW.equals(property) || DIFF_PREVIEW_VERTICAL_SPLIT.equals(property) ||
           SHOW_CHANGES_FROM_PARENTS.equals(property) || COLUMN_ID_ORDER.equals(property) || PREFER_COMMIT_DATE.equals(property) ||
           property instanceof TableColumnVisibilityProperty;
  }

  @Override
  public void addChangeListener(@NotNull VcsLogUiProperties.PropertiesChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@NotNull VcsLogUiProperties.PropertiesChangeListener listener) {
    myListeners.remove(listener);
  }

  public static class State {
    public boolean COMPACT_REFERENCES_VIEW = true;
    public boolean SHOW_TAG_NAMES = false;
    public boolean LABELS_LEFT_ALIGNED = Registry.is("vcs.log.labels.left.aligned");
    public boolean SHOW_CHANGES_FROM_PARENTS = false;
    public boolean SHOW_DIFF_PREVIEW = false;
    public boolean DIFF_PREVIEW_VERTICAL_SPLIT = true;
    public boolean PREFER_COMMIT_DATE = false;
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
    public List<Integer> COLUMN_ORDER = new ArrayList<>();
    public List<String> COLUMN_ID_ORDER = new ArrayList<>();
    public Map<String, Boolean> COLUMN_ID_VISIBILITY = new HashMap<>();
    public Map<String, Boolean> CUSTOM_BOOLEAN_PROPERTIES = new HashMap<>();
  }

  public static class CustomBooleanProperty extends VcsLogUiProperties.VcsLogUiProperty<Boolean> {
    public CustomBooleanProperty(@NotNull @NonNls String name) {
      super(name);
    }

    @NotNull
    public Boolean defaultValue() {
      return Boolean.FALSE;
    }
  }
}
