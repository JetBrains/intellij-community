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

import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

public abstract class GenericNotifierImpl<T, Key> {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.GenericNotifier");
  protected final Project myProject;
  @NotNull
  private final String myGroupId; //+- here
  @NotNull
  private final String myTitle;
  @NotNull
  private final NotificationType myType;
  @NotNull
  private final Map<Key, MyNotification> myState;
  private final MyListener myListener;
  private final Object myLock;

  protected GenericNotifierImpl(final Project project, @NotNull String groupId, @NotNull String title, final @NotNull NotificationType type) {
    myGroupId = groupId;
    myTitle = title;
    myType = type;
    myProject = project;
    myState = new HashMap<Key, MyNotification>();
    myListener = new MyListener();
    myLock = new Object();
  }

  protected abstract boolean ask(final T obj, String description);
  @NotNull
  protected abstract Key getKey(final T obj);
  @NotNull
  protected abstract String getNotificationContent(final T obj);

  protected Collection<Key> getAllCurrentKeys() {
    synchronized (myLock) {
      return new ArrayList<>(myState.keySet());
    }
  }

  protected boolean getStateFor(final Key key) {
    synchronized (myLock) {
      return myState.containsKey(key);
    }
  }

  public void clear() {
    final List<MyNotification> notifications;
    synchronized (myLock) {
      notifications = new ArrayList<MyNotification>(myState.values());
      myState.clear();
    }
    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = new Runnable() {
      public void run() {
        for (MyNotification notification : notifications) {
          notification.expire();
        }
      }
    };
    if (application.isDispatchThread()) {
      runnable.run();
    } else {
      application.invokeLater(runnable, ModalityState.NON_MODAL, myProject.getDisposed());
    }
  }

  private void expireNotification(final MyNotification notification) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        notification.expire();
      }
    });
  }

  public boolean ensureNotify(final T obj) {
    final MyNotification notification;
    synchronized (myLock) {
      final Key key = getKey(obj);
      if (myState.containsKey(key)) {
        return false;
      }
      notification = new MyNotification(myGroupId, myTitle, getNotificationContent(obj), myType, myListener, obj);
      myState.put(key, notification);
    }
    final boolean state = onFirstNotification(obj);
    if (state) {
      removeLazyNotification(obj);
      return true;
    }
    Notifications.Bus.notify(notification, myProject);
    return false;
  }

  protected boolean onFirstNotification(T obj) {
    return false;
  }

  public void removeLazyNotificationByKey(final Key key) {
    final MyNotification notification;
    synchronized (myLock) {
      notification = myState.get(key);
      if (notification != null) {
        myState.remove(key);
      }
    }
    if (notification != null) {
      expireNotification(notification);
    }
  }

  public void removeLazyNotification(final T obj) {
    final MyNotification notification;
    synchronized (myLock) {
      final Key key = getKey(obj);
      notification = myState.get(key);
      if (notification != null) {
        myState.remove(key);
      }
    }
    if (notification != null) {
      expireNotification(notification);
    }
  }

  private class MyListener implements NotificationListener {
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      final MyNotification concreteNotification = (MyNotification) notification;
      final T obj = concreteNotification.getObj();
      final boolean state = ask(obj, event.getDescription());
      if (state) {
        synchronized (myLock) {
          final Key key = getKey(obj);
          myState.remove(key);
        }
        expireNotification(concreteNotification);
      }
    }
  }

  @Nullable
  protected T getObj(final Key key) {
    synchronized (myLock) {
      final MyNotification notification = myState.get(key);
      return notification == null ? null : notification.getObj();
    }
  }

  protected class MyNotification extends Notification {
    private final T myObj;

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

    @Override
    public void expire() {
      super.expire();
      synchronized (myLock) {
        myState.remove(getKey(myObj));
      }
    }
  }

  private static void log(final String s) {
    LOG.debug(s);
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myState.isEmpty();
    }
  }
}
