// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
@State(name = "VcsContentAnnotationSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class VcsContentAnnotationSettings implements PersistentStateComponent<VcsContentAnnotationSettings.State> {
  public static final int ourMaxDays = 31; // approx
  static final long ourAbsoluteLimit = TimeUnit.DAYS.toMillis(ourMaxDays);

  private State myState = new State();

  public static VcsContentAnnotationSettings getInstance(final Project project) {
    return project.getService(VcsContentAnnotationSettings.class);
  }

 public static final class State {
    public boolean myShow1;
    public long myLimit = ourAbsoluteLimit;
  }

  @Override
  public @NotNull State getState() {
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

  public void setLimitDays(int days) {
    myState.myLimit = TimeUnit.DAYS.toMillis(days);
  }

  public boolean isShow() {
    return myState.myShow1;
  }

  public void setShow(final boolean value) {
    myState.myShow1 = value;
  }
}
