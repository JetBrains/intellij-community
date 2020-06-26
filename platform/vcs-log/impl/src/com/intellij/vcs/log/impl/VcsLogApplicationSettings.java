// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
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

  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperty<T> property) {
    return property.match()
      .ifEq(COMPACT_REFERENCES_VIEW).then(myState.COMPACT_REFERENCES_VIEW)
      .ifEq(SHOW_TAG_NAMES).then(myState.SHOW_TAG_NAMES)
      .ifEq(LABELS_LEFT_ALIGNED).then(myState.LABELS_LEFT_ALIGNED)
      .ifEq(SHOW_CHANGES_FROM_PARENTS).then(myState.SHOW_CHANGES_FROM_PARENTS)
      .ifEq(SHOW_DIFF_PREVIEW).then(myState.SHOW_DIFF_PREVIEW)
      .ifEq(DIFF_PREVIEW_VERTICAL_SPLIT).then(myState.DIFF_PREVIEW_VERTICAL_SPLIT)
      .ifEq(PREFER_COMMIT_DATE).then(myState.PREFER_COMMIT_DATE)
      .ifEq(COLUMN_ORDER).thenGet(() -> {
        List<Integer> order = myState.COLUMN_ORDER;
        if (order == null || order.isEmpty()) {
          order = ContainerUtil.map(Arrays.asList(VcsLogColumn.ROOT, VcsLogColumn.COMMIT, VcsLogColumn.AUTHOR, VcsLogColumn.DATE),
                                    VcsLogColumn::ordinal);
        }
        return order;
      })
      .get();
  }

  @Override
  public <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value) {
    if (COMPACT_REFERENCES_VIEW.equals(property)) {
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
    else if (COLUMN_ORDER.equals(property)) {
      //noinspection unchecked
      myState.COLUMN_ORDER = (List<Integer>)value;
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperty<T> property) {
    return COMPACT_REFERENCES_VIEW.equals(property) || SHOW_TAG_NAMES.equals(property) || LABELS_LEFT_ALIGNED.equals(property) ||
           SHOW_DIFF_PREVIEW.equals(property) || DIFF_PREVIEW_VERTICAL_SPLIT.equals(property) ||
           SHOW_CHANGES_FROM_PARENTS.equals(property) || COLUMN_ORDER.equals(property) || PREFER_COMMIT_DATE.equals(property);
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
    public List<Integer> COLUMN_ORDER = new ArrayList<>();
  }
}
