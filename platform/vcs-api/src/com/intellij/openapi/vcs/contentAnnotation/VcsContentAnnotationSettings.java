// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * @author Irina.Chernushina
 * @since 3.08.2011
 */
@State(
  name = "VcsContentAnnotationSettings",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class VcsContentAnnotationSettings implements PersistentStateComponent<VcsContentAnnotationSettings.State> {
  public static final int ourMaxDays = 31; // approx
  public static final long ourAbsoluteLimit = TimeUnit.DAYS.toMillis(ourMaxDays);

  private State myState = new State();
  {
    myState.myLimit = ourAbsoluteLimit;
  }

  public static VcsContentAnnotationSettings getInstance(final Project project) {
    return ServiceManager.getService(project, VcsContentAnnotationSettings.class);
  }
  
  public static class State {
    public boolean myShow1;
    public long myLimit;
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public long getLimit() {
    return myState.myLimit;
  }

  public int getLimitDays() {
    return (int)TimeUnit.MILLISECONDS.toDays(myState.myLimit);
  }

  public void setLimit(int days) {
    myState.myLimit = TimeUnit.DAYS.toMillis(days);
  }

  public boolean isShow() {
    return myState.myShow1;
  }

  public void setShow(final boolean value) {
    myState.myShow1 = value;
  }
}
