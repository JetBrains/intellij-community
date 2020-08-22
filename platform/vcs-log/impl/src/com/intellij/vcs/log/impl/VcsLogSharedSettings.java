// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

@State(name = "Vcs.Log.Settings", storages = @Storage("vcs.xml"))
public class VcsLogSharedSettings implements PersistentStateComponent<VcsLogSharedSettings.State> {
  private State myState = new State();

  public static final class State {
    @Attribute("index") public boolean IS_INDEX_ON = true;
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public boolean isIndexSwitchedOn() {
    return getState().IS_INDEX_ON;
  }

  public static boolean isIndexSwitchedOn(@NotNull Project project) {
    VcsLogSharedSettings indexSwitch = ServiceManager.getService(project, VcsLogSharedSettings.class);
    return indexSwitch.isIndexSwitchedOn() || Registry.is("vcs.log.index.force");
  }
}
