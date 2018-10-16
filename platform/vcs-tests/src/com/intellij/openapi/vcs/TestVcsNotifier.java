// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class TestVcsNotifier extends VcsNotifier {

  private static final String TEST_NOTIFICATION_GROUP = "Test";

  private Notification myLastNotification;

  public TestVcsNotifier(@NotNull Project project) {
    super(project);
  }

  public Notification getLastNotification() {
    return myLastNotification;
  }

  @Override
  @NotNull
  public Notification notify(@NotNull Notification notification) {
    myLastNotification = notification;
    return myLastNotification;
  }

  public void cleanup() {
    myLastNotification = null;
  }
}
