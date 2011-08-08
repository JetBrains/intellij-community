/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/11
 * Time: 1:13 PM
 */
@State(
  name = "VcsContentAnnotationSettings",
  storages = {@Storage( file = "$WORKSPACE_FILE$")})
public class VcsContentAnnotationSettings implements PersistentStateComponent<VcsContentAnnotationSettings.State> {
  // approx
  public static final int ourMaxDays = 31;
  public final static long ourAbsoluteLimit = ourMaxDays * 24 * 60 * 60 * 1000L;
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

  public long getLimitDays() {
    return myState.myLimit / (24 * 60 * 60 * 1000L);
  }

  public void setLimit(long limit) {
    myState.myLimit = limit * 24 * 60 * 60 * 1000L;
  }

  public boolean isShow() {
    return myState.myShow1;
  }

  public void setShow(final boolean value) {
    myState.myShow1 = value;
  }
}
