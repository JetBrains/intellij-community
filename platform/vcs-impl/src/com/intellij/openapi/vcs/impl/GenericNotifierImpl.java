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
package com.intellij.openapi.vcs.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

public abstract class GenericNotifierImpl<T, Key> {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.GenericNotifier");
  private final Project myProject;
  @NotNull
  private final String myGroupId; //+- here
  @NotNull
  private final String myTitle;
  @NotNull
  private final NotificationType myType;
  @NotNull
  private final Map<Key, MyNotification<T>> myState;
  private final MyListener myListener;
  private final Object myLock;

  protected GenericNotifierImpl(final Project project, @NotNull String groupId, @NotNull String title, final @NotNull NotificationType type) {
    myGroupId = groupId;
    myTitle = title;
    myType = type;
    myProject = project;
    myState = new HashMap<Key, MyNotification<T>>();
    myListener = new MyListener();
    myLock = new Object();
  }

  protected abstract boolean ask(final T obj);
  @NotNull
  protected abstract Key getKey(final T obj);
  @NotNull
  protected abstract String getNotificationContent(final T obj);

  protected Collection<Key> getAllCurrentKeys() {
    synchronized (myLock) {
      return new ArrayList<Key>(myState.keySet());
    }
  }

  protected boolean getStateFor(final Key key) {
    synchronized (myLock) {
      return myState.containsKey(key);
    }
  }

  public void clear() {
    final List<MyNotification<T>> notifications;
    synchronized (myLock) {
      notifications = new ArrayList<MyNotification<T>>(myState.values());
      myState.clear();
    }
    for (MyNotification<T> notification : notifications) {
      notification.expire();
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
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      Notifications.Bus.notify(notification, myProject);
    } else {
      application.invokeLater(new Runnable() {
        public void run() {
          Notifications.Bus.notify(notification, myProject);
        }
      });
    }
  }

  public void removeLazyNotificationByKey(final Key key) {
    final MyNotification<T> notification;
    synchronized (myLock) {
      notification = myState.get(key);
      if (notification != null) {
        myState.remove(key);
      }
    }
    if (notification != null) {
      notification.expire();
    }
  }

  public void removeLazyNotification(final T obj) {
    final MyNotification<T> notification;
    synchronized (myLock) {
      final Key key = getKey(obj);
      notification = myState.get(key);
      if (notification != null) {
        myState.remove(key);
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
          }
          notification.expire();
        }
      }
    }
  }

  @Nullable
  protected T getObj(final Key key) {
    synchronized (myLock) {
      final MyNotification<T> notification = myState.get(key);
      return notification == null ? null : notification.getObj();
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

  private static void log(final String s) {
    LOG.debug(s);
  }
}
