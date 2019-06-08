// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TestVcsNotifier extends VcsNotifier {

  private static final String TEST_NOTIFICATION_GROUP = "Test";

  private final List<Notification> myNotifications = new ArrayList<>();

  public TestVcsNotifier(@NotNull Project project) {
    super(project);
  }

  public Notification getLastNotification() {
    return ContainerUtil.getLastItem(myNotifications);
  }

  @Nullable
  public Notification findExpectedNotification(@NotNull Notification expectedNotification) {
    return myNotifications
      .stream()
      .filter((notification ->
                 expectedNotification.getType().equals(notification.getType())
                 && expectedNotification.getTitle().equals(notification.getTitle())
                 && expectedNotification.getContent().equals(notification.getContent())))
      .findAny().orElse(null);
  }

  @Override
  @NotNull
  public Notification notify(@NotNull Notification notification) {
    myNotifications.add(notification);
    return notification;
  }

  @NotNull
  public List<Notification> getNotifications(){
    return ContainerUtil.unmodifiableOrEmptyList(myNotifications);
  }

  public void cleanup() {
    myNotifications.clear();
  }
}
