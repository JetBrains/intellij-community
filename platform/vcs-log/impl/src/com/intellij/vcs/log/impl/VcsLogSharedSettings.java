/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

@State(name = "Vcs.Log.Settings", storages = {@Storage("vcs.xml")})
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
  public void loadState(State state) {
    myState = state;
  }

  public static boolean isIndexSwitchedOn(@NotNull Project project) {
    VcsLogSharedSettings indexSwitch = ServiceManager.getService(project, VcsLogSharedSettings.class);
    return indexSwitch.getState().IS_INDEX_ON || Registry.is("vcs.log.index.force");
  }
}
