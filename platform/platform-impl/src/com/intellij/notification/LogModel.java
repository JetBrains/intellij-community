// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

@Deprecated
public final class LogModel  {
  private final Project myProject;

  LogModel(@Nullable Project project) {
    myProject = project;
  }

  public @NotNull ArrayList<Notification> getNotifications() {
    return new ArrayList<>(ActionCenter.getNotifications(myProject));
  }
}
