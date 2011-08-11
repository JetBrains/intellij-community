/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairFunction;
import com.intellij.util.concurrency.ReentrantLock2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author spleaner
 */
public class NotificationModel {

  private final Map<Notification, Pair<Project, Boolean>> myNotifications = new LinkedHashMap<Notification, Pair<Project, Boolean>>();

  private final ReentrantLock2 myLock = new ReentrantLock2();

  public void add(@NotNull final Notification notification, final @Nullable Project project) {
    try {
      myLock.acquire();
      myNotifications.put(notification, Pair.create(project, false));
    }
    finally {
      myLock.release();
    }
  }

  @Nullable
  public Notification remove(@NotNull final Notification notification) {
    try {
      myLock.acquire();
      myNotifications.remove(notification);
    }
    finally {
      myLock.release();
    }

    return notification;
  }

  public void remove(@NotNull final Notification... notifications) {
    try {
      myLock.acquire();
      for (final Notification notification : notifications) {
        myNotifications.remove(notification);
      }
    }
    finally {
      myLock.release();
    }
  }

  private List<Notification> filterNotifications(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    LinkedList<Notification> result;
    if (myNotifications.isEmpty()) return Collections.emptyList();

    result = new LinkedList<Notification>();
    for (final Map.Entry<Notification, Pair<Project, Boolean>> entry : myNotifications.entrySet()) {
      //noinspection ConstantConditions
      if (filter.fun(entry.getKey(), entry.getValue().first)) {
        result.addFirst(entry.getKey());
      }
    }

    return result;
  }

  public void clear(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    List<Notification> result;
    try {
      myLock.acquire();
      result = filterNotifications(filter);
      myNotifications.keySet().removeAll(result);
    }
    finally {
      myLock.release();
    }
  }

  public List<Notification> getByType(@Nullable final NotificationType type, @NotNull PairFunction<Notification, Project, Boolean> filter) {
    if (type == null) {
      try {
        myLock.acquire();
        return Collections.unmodifiableList(filterNotifications(filter));
      }
      finally {
        myLock.release();
      }
    }

    final List<Notification> filtered;
    try {
      myLock.acquire();
      filtered = filterNotifications(filter);
    }
    finally {
      myLock.release();
    }

    final List<Notification> result = new ArrayList<Notification>();
    for (final Notification notification : filtered) {
      if (type == notification.getType()) {
        result.add(notification);
      }
    }

    return result;
  }

  @Nullable
  public static NotificationType getMaximumType(List<Notification> notifications) {
    NotificationType result = null;
    for (Notification notification : notifications) {
      if (NotificationType.ERROR == notification.getType()) {
        return NotificationType.ERROR;
      }

      if (NotificationType.WARNING == notification.getType()) {
        result = NotificationType.WARNING;
      }
      else if (result == null && NotificationType.INFORMATION == notification.getType()) {
        result = NotificationType.INFORMATION;
      }
    }

    return result;
  }
}
