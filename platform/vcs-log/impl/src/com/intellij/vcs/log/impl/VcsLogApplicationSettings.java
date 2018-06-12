// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.vcs.log.impl.CommonUiProperties.SHOW_DIFF_PREVIEW;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;

@State(name = "Vcs.Log.App.Settings", storages = {@Storage("vcs.xml")})
public class VcsLogApplicationSettings implements PersistentStateComponent<VcsLogApplicationSettings.State>, VcsLogUiProperties {
  @NotNull private final Set<VcsLogUiProperties.PropertiesChangeListener> myListeners = ContainerUtil.newLinkedHashSet();
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
    if (COMPACT_REFERENCES_VIEW.equals(property)) {
      return (T)Boolean.valueOf(myState.COMPACT_REFERENCES_VIEW);
    }
    else if (SHOW_TAG_NAMES.equals(property)) {
      return (T)Boolean.valueOf(myState.SHOW_TAG_NAMES);
    }
    else if (SHOW_CHANGES_FROM_PARENTS.equals(property)) {
      return (T)Boolean.valueOf(myState.SHOW_CHANGES_FROM_PARENTS);
    }
    else if (SHOW_DIFF_PREVIEW.equals(property)) {
      return (T)Boolean.valueOf(myState.SHOW_DIFF_PREVIEW);
    }
    throw new UnsupportedOperationException("Property " + property + " does not exist");
  }

  @Override
  public <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value) {
    if (COMPACT_REFERENCES_VIEW.equals(property)) {
      myState.COMPACT_REFERENCES_VIEW = (Boolean)value;
    }
    else if (SHOW_TAG_NAMES.equals(property)) {
      myState.SHOW_TAG_NAMES = (Boolean)value;
    }
    else if (SHOW_CHANGES_FROM_PARENTS.equals(property)) {
      myState.SHOW_CHANGES_FROM_PARENTS = (Boolean)value;
    }
    else if (SHOW_DIFF_PREVIEW.equals(property)) {
      myState.SHOW_DIFF_PREVIEW = (Boolean)value;
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperty<T> property) {
    return COMPACT_REFERENCES_VIEW.equals(property) || SHOW_TAG_NAMES.equals(property) ||
           SHOW_CHANGES_FROM_PARENTS.equals(property) || SHOW_DIFF_PREVIEW.equals(property);
  }

  public void addChangeListener(@NotNull VcsLogUiProperties.PropertiesChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(@NotNull VcsLogUiProperties.PropertiesChangeListener listener) {
    myListeners.remove(listener);
  }

  public static class State {
    public boolean COMPACT_REFERENCES_VIEW = true;
    public boolean SHOW_TAG_NAMES = false;
    public boolean SHOW_CHANGES_FROM_PARENTS = false;
    public boolean SHOW_DIFF_PREVIEW = false;
  }
}
