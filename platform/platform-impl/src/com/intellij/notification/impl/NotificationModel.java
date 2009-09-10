package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairFunction;
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

  public void addListener(@NotNull final NotificationModelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull final NotificationModelListener listener) {
    myListeners.remove(listener);
  }

  public void add(@NotNull final Notification notification, final @Nullable Project project) {
    myNotifications.put(notification, Pair.create(project, false));
    for (NotificationModelListener listener : myListeners) {
      listener.notificationsAdded(notification);
    }
  }

  public void markRead() {
    if (myNotifications.size() > 0) {
      final Collection<Notification> tba = myNotifications.keySet();
      for (final Map.Entry<Notification, Pair<Project, Boolean>> entry : myNotifications.entrySet()) {
        entry.setValue(new Pair<Project, Boolean>(entry.getValue().first, true));
      }

      for (final NotificationModelListener listener : myListeners) {
        listener.notificationsRead(tba.toArray(new Notification[tba.size()]));
      }
    }
  }

  @Nullable
  public Notification remove(@NotNull final Notification notification) {
    if (myNotifications.containsKey(notification)) {
      myNotifications.remove(notification);

      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(notification);
      }
    }

    return notification;
  }

  public void remove(@NotNull final Notification... notifications) {
    final List<Notification> tbr = new ArrayList<Notification>();
    for (final Notification notification : notifications) {
      if (myNotifications.containsKey(notification)) {
        tbr.add(notification);
        myNotifications.remove(notification);
      }
    }

    if (tbr.size() > 0) {
      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(tbr.toArray((Notification[])Array.newInstance(tbr.get(0).getClass(), tbr.size())));
      }
    }
  }

  @Nullable
  public Notification get(final int index, @NotNull PairFunction<Notification, Project, Boolean> filter) {
    final LinkedList<Notification> filtered = filterNotifications(filter);
    if (index >= 0 && filtered.size() > index) {
      return filtered.get(index);
    }

    return null;
  }

  private LinkedList<Notification> filterNotifications(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    final LinkedList<Notification> result = new LinkedList<Notification>();
    for (final Map.Entry<Notification, Pair<Project, Boolean>> entry : myNotifications.entrySet()) {
      if (filter.fun(entry.getKey(), entry.getValue().first)) {
        result.addFirst(entry.getKey());
      }
    }

    return result;
  }

  public int getCount(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    return filterNotifications(filter).size();
  }

  public boolean isEmpty(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    return getCount(filter) == 0;
  }

  @Nullable
  public Notification getFirst(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    final LinkedList<Notification> result = filterNotifications(filter);
    if (result.size() > 0) {
      return result.getFirst();
    }

    return null;
  }

  public void clear(@NotNull PairFunction<Notification, Project, Boolean> filter) {
    final LinkedList<Notification> result = filterNotifications(filter);
    for (final Notification notification : result) {
      myNotifications.remove(notification);
    }

    if (!result.isEmpty()) {
      final Notification[] removed = result.toArray((Notification[])Array.newInstance(result.get(0).getClass(), result.size()));
      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(removed);
      }
    }
  }

  public List<Notification> getByType(@Nullable final NotificationType type, @NotNull PairFunction<Notification, Project, Boolean> filter) {
    if (type == null) {
      return Collections.unmodifiableList(filterNotifications(filter));
    }
    else {
      final List<Notification> result = new ArrayList<Notification>();
      final LinkedList<Notification> filtered = filterNotifications(filter);
      for (final Notification notification : filtered) {
        if (type == notification.getType()) {
          result.add(notification);
        }
      }

      return result;
    }
  }

  public boolean wasRead(final Notification notification) {
    final Pair<Project, Boolean> pair = myNotifications.get(notification);
    return pair != null && pair.second;
  }

  public boolean hasUnread(final PairFunction<Notification, Project, Boolean> filter) {
    return getUnreadCount(filter) > 0;
  }

  private int getUnreadCount(final PairFunction<Notification, Project, Boolean> filter) {
    return filterNotifications(new PairFunction<Notification, Project, Boolean>() {
      public Boolean fun(Notification notification, Project project) {
        return filter.fun(notification, project) && !wasRead(notification);
      }
    }).size();
  }

  public boolean hasRead(PairFunction<Notification, Project, Boolean> filter) {
    return getUnreadCount(filter) < myNotifications.size();
  }
}
