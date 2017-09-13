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
package com.intellij.coverage;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

@State(
  name = "JavaCoverageOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class JavaCoverageOptionsProvider implements PersistentStateComponent<JavaCoverageOptionsProvider.State> {
  private State myState = new State();
  
  public static JavaCoverageOptionsProvider getInstance(Project project) {
    return ServiceManager.getService(project, JavaCoverageOptionsProvider.class);
  }

  
  public void setIgnoreEmptyPrivateConstructors(boolean state) {
    myState.myIgnoreEmptyPrivateConstructors = state;
  }
  
  public boolean ignoreEmptyPrivateConstructors() {
    return myState.myIgnoreEmptyPrivateConstructors;
  }

  @Nullable
  @Override
  public JavaCoverageOptionsProvider.State getState() {
    return myState;
  }

  @Override
  public void loadState(JavaCoverageOptionsProvider.State state) {
     myState.myIgnoreEmptyPrivateConstructors = state.myIgnoreEmptyPrivateConstructors;
  }
  
  
  public static class State {
    public boolean myIgnoreEmptyPrivateConstructors = true;
  }

}
