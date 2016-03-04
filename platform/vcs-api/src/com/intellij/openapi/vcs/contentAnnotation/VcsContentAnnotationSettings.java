/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;

/**
 * @author Irina.Chernushina
 * @since 3.08.2011
 */
@State(
  name = "VcsContentAnnotationSettings",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class VcsContentAnnotationSettings implements PersistentStateComponent<VcsContentAnnotationSettings.State> {
  public static final long ourMillisecondsInDay = 24 * 60 * 60 * 1000L;
  public static final int ourMaxDays = 31; // approx
  public static final long ourAbsoluteLimit = ourMillisecondsInDay * ourMaxDays;

  private State myState = new State();
  {
    myState.myLimit = ourAbsoluteLimit;
  }

  public static VcsContentAnnotationSettings getInstance(final Project project) {
    return ServiceManager.getService(project, VcsContentAnnotationSettings.class);
  }
  
  public static class State {
    public boolean myShow1 = false;
    public long myLimit;
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public long getLimit() {
    return myState.myLimit;
  }

  public int getLimitDays() {
    return (int) (myState.myLimit / ourMillisecondsInDay);
  }

  public void setLimit(int limit) {
    myState.myLimit = ourMillisecondsInDay * limit;
  }

  public boolean isShow() {
    return myState.myShow1;
  }

  public void setShow(final boolean value) {
    myState.myShow1 = value;
  }
}
