/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
@State(
  name = "HgRememberedInputs",
  storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE)
)
public class HgRememberedInputs implements PersistentStateComponent<HgRememberedInputs.State> {
  private State myState = new State();

  public static class State {
    public List<String> repositoryUrls = new ArrayList<String>();
  }


  public static HgRememberedInputs getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, HgRememberedInputs.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public void addRepositoryUrl(@NotNull String url) {
    if (!myState.repositoryUrls.contains(url)) {  // don't add multiple entries for a single path
      myState.repositoryUrls.add(url);
    }
  }

  @NotNull
  public List<String> getRepositoryUrls() {
    return myState.repositoryUrls;
  }
}
