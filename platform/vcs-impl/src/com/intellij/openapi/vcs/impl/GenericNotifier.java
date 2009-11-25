package com.intellij.openapi.vcs.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class GenericNotifier<T, Key> {
  private final Project myProject;
  @NotNull
  private final String myGroupId; //+- here
  @NotNull
  private final String myTitle;
  @NotNull
  private final NotificationType myType;
  @NotNull
  private final Map<Key, MyNotification<T>> myState;
  @NotNull
  private final Map<Key, Boolean> myWasCancelled;
  private final MyListener myListener;
  private final Object myLock;

  protected GenericNotifier(final Project project, @NotNull String groupId, @NotNull String title, final @NotNull NotificationType type) {
    myGroupId = groupId;
    myTitle = title;
    myType = type;
    myProject = project;
    myState = new HashMap<Key, MyNotification<T>>();
    myListener = new MyListener();
    myLock = new Object();
    myWasCancelled = new HashMap<Key, Boolean>();
  }

  protected abstract boolean ask(final T obj);
  @NotNull
  protected abstract Key getKey(final T obj);
  @NotNull
  protected abstract String getNotificationContent(final T obj);

  protected Set<Key> canceledKeySet() {
    synchronized (myLock) {
      return new HashSet<Key>(myWasCancelled.keySet());
    }
  }

  public void triggerAsk(final Key key) {
    synchronized (myLock) {
      myWasCancelled.put(key, true);
    }
  }

  /**
   * @return true === ask interactively. otherwise queue notification
   */
  public boolean retrieveTicket(final T obj) {
    synchronized (myLock) {
      final Key key = getKey(obj);
      final Boolean value = myWasCancelled.get(key);
      if (value == null) {
        myWasCancelled.put(key, false);
        return true;
      }
      if (value) {
        myWasCancelled.put(key, false);
      }
      return value;
    }
  }

  public void ensureNotify(final T obj) {
    final MyNotification<T> notification;
    synchronized (myLock) {
      final Key key = getKey(obj);
      if (myState.containsKey(key)) {
        return;
      }
      notification = new MyNotification<T>(myGroupId, myTitle, getNotificationContent(obj), myType, myListener, obj);
      myState.put(key, notification);
    }
    Notifications.Bus.notify(notification, myProject);
  }

  public void removeLazyNotification(final T obj) {
    final MyNotification<T> notification;
    synchronized (myLock) {
      final Key key = getKey(obj);
      notification = myState.get(key);
      if (notification != null) {
        myState.remove(key);
        myWasCancelled.remove(key);
      }
    }
    if (notification != null) {
      notification.expire();
    }
  }

  private class MyListener implements NotificationListener {
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (notification instanceof MyNotification) {
        final MyNotification<T> concreteNotification = (MyNotification<T>) notification;
        final T obj = concreteNotification.getObj();
        final boolean state = ask(obj);
        if (state) {
          synchronized (myLock) {
            final Key key = getKey(obj);
            myState.remove(key);
            myWasCancelled.remove(key);
          }
          notification.expire();
        }
      }
    }
  }

  protected static class MyNotification<T> extends Notification {
    private T myObj;

    protected MyNotification(@NotNull String groupId,
                           @NotNull String title,
                           @NotNull String content,
                           @NotNull NotificationType type,
                           @Nullable NotificationListener listener,
                           @NotNull final T obj) {
      super(groupId, title, content, type, listener);
      myObj = obj;
    }

    public T getObj() {
      return myObj;
    }
  }
}
