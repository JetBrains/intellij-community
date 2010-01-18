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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @author spleaner
 */
public class NotificationModel {

  private final Map<Notification, Pair<Project, Boolean>> myNotifications = new LinkedHashMap<Notification, Pair<Project, Boolean>>();
  private final List<NotificationModelListener> myListeners = ContainerUtil.createEmptyCOWList();

  private final ReentrantLock2 myLock = new ReentrantLock2();

  public void addListener(@NotNull final NotificationModelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull final NotificationModelListener listener) {
    myListeners.remove(listener);
  }

  public void add(@NotNull final Notification notification, final @Nullable Project project) {
    try {
      myLock.acquire();
      myNotifications.put(notification, Pair.create(project, false));
    }
    finally {
      myLock.release();
    }

    for (NotificationModelListener listener : myListeners) {
      listener.notificationsAdded(notification);
    }

  }

  public void markRead() {
    List<Notification> changed = null;

    try {
      myLock.acquire();
      if (myNotifications.isEmpty()) return;

      changed = new ArrayList<Notification>();

      for (final Map.Entry<Notification, Pair<Project, Boolean>> entry : myNotifications.entrySet()) {
        entry.setValue(new Pair<Project, Boolean>(entry.getValue().first, true));
        changed.add(entry.getKey());
      }
    }
    finally {
      myLock.release();
    }

    if (!changed.isEmpty()) {
      final Notification[] read = changed.toArray(new Notification[changed.size()]);
      for (final NotificationModelListener listener : myListeners) {
        listener.notificationsRead(read);
      }
    }
  }

  @Nullable
  public Notification remove(@NotNull final Notification notification) {
    try {
      myLock.acquire();
      final Pair<Project, Boolean> pair = myNotifications.remove(notification);
      if (pair == null) return notification;
    }
    finally {
      myLock.release();
    }

    for (NotificationModelListener listener : myListeners) {
      listener.notificationsRemoved(notification);
    }

    return notification;
  }

  public void remove(@NotNull final Notification... notifications) {
    final List<Notification> tbr = new ArrayList<Notification>();
    try {
      myLock.acquire();
      for (final Notification notification : notifications) {
        final Pair<Project, Boolean> pair = myNotifications.remove(notification);
        if (pair != null) {
          tbr.add(notification);
        }
      }
    }
    finally {
      myLock.release();
    }

    if (!tbr.isEmpty()) {
      final Notification[] removed = tbr.toArray((Notification[])Array.newInstance(tbr.get(0).getClass(), tbr.size()));
      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(removed);
      }
    }
  }

  @Nullable
  public Notification get(final int index, @NotNull PairFunction<Notification, Project, Boolean> filter) {
    try {
      myLock.acquire();
      final List<Notification> filtered = filterNotifications(filter);
      if (index >= 0 && filtered.size() > index) {
        return filtered.get(index);
      }
    }
    finally {
      myLock.release();
    }

    return null;
  }

  private List<Notification> filterNotifications(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    LinkedList<Notification> result;
    if (myNotifications.isEmpty()) return Collections.emptyList();

    result = new LinkedList<Notification>();
    for (final Map.Entry<Notification, Pair<Project, Boolean>> entry : myNotifications.entrySet()) {
      if (filter.fun(entry.getKey(), entry.getValue().first)) {
        result.addFirst(entry.getKey());
      }
    }

    return result;
  }

  public int getCount(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    try {
      myLock.acquire();
      return filterNotifications(filter).size();
    }
    finally {
      myLock.release();
    }
  }

  public boolean isEmpty(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    return getCount(filter) == 0;
  }

  @Nullable
  public Notification getFirst(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    try {
      myLock.acquire();
      final List<Notification> result = filterNotifications(filter);
      return result.isEmpty() ? null : result.get(0);
    }
    finally {
      myLock.release();
    }
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

    if (!result.isEmpty()) {
      final Notification[] removed = result.toArray(new Notification[result.size()]);
      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(removed);
      }
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

  public boolean wasRead(final Notification notification) {
    try {
      myLock.acquire();
      final Pair<Project, Boolean> pair = myNotifications.get(notification);
      return pair != null && pair.second;
    }
    finally {
      myLock.release();
    }
  }

  public boolean hasUnread(final PairFunction<Notification, Project, Boolean> filter) {
    try {
      myLock.acquire();
      return getUnreadCount(filter) > 0;
    }
    finally {
      myLock.release();
    }
  }

  private int getUnreadCount(final PairFunction<Notification, Project, Boolean> filter) {
    return filterNotifications(new PairFunction<Notification, Project, Boolean>() {
      public Boolean fun(Notification notification, Project project) {
        return filter.fun(notification, project) && !wasRead(notification);
      }
    }).size();
  }

  public boolean hasRead(PairFunction<Notification, Project, Boolean> filter) {
    try {
      myLock.acquire();
      return getUnreadCount(filter) < myNotifications.size();
    }
    finally {
      myLock.release();
    }
  }

  @Nullable
  public NotificationType getMaximumType(PairFunction<Notification, Project, Boolean> filter) {
    final List<Notification> notifications;
    try {
      myLock.acquire();
      notifications = filterNotifications(filter);
    }
    finally {
      myLock.release();
    }

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
