// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.messages.MessageBusConnection;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.ui.mac.foundation.Foundation.invoke;
import static com.intellij.ui.mac.foundation.Foundation.nsString;

final class MacOsNotifications implements SystemNotificationsImpl.Notifier {
  private static final String ACTIVATION_ID_USER_INFO_KEY = "intellij-system-notification-activation-id";
  private static final String NOTIFICATION_DELEGATE_CLASS_NAME = "IdeaSystemNotificationDelegate";
  private static final int MAX_STORED_ACTIVATION_CALLBACKS = 32;

  private static MacOsNotifications ourInstance;

  private final Map<String, Runnable> myCallbacksByActivationId = new ConcurrentHashMap<>();
  @SuppressWarnings("FieldCanBeLocal")
  private final Callback myNotificationActivationCallback;
  @SuppressWarnings("FieldCanBeLocal")
  private final ID myDelegate;

  static synchronized @NotNull MacOsNotifications getInstance() {
    if (ourInstance == null && JnaLoader.isLoaded()) {
      ourInstance = new MacOsNotifications();
    }
    return ourInstance;
  }

  private MacOsNotifications() {
    myNotificationActivationCallback = new Callback() {
      @SuppressWarnings("unused")
      public void callback(ID self, Pointer selector, ID center, ID notification) {
        notificationActivated(notification);
      }
    };
    myDelegate = createDelegate(myNotificationActivationCallback);
    ID center = notificationCenter();
    if (!ID.NIL.equals(myDelegate)) {
      invoke(center, "setDelegate:", myDelegate);
    }

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(@NotNull IdeFrame ideFrame) {
        removeDeliveredNotifications();
      }
    });
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        cleanupDeliveredNotifications();
      }
    });
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    notify(name, title, description, null);
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description, @Nullable Runnable onActivated) {
    ID notification = invoke(Foundation.getObjcClass("NSUserNotification"), "new");
    invoke(notification, "setTitle:", nsString(StringUtil.stripHtml(title, true).replace("%", "%%")));
    invoke(notification, "setInformativeText:", nsString(StringUtil.stripHtml(description, true).replace("%", "%%")));

    if (onActivated != null) {
      String activationId = UUID.randomUUID().toString();
      if (myCallbacksByActivationId.size() >= MAX_STORED_ACTIVATION_CALLBACKS) {
        myCallbacksByActivationId.clear();
      }
      myCallbacksByActivationId.put(activationId, onActivated);
      invoke(notification, "setUserInfo:", Foundation.createDict(new String[]{ACTIVATION_ID_USER_INFO_KEY}, new Object[]{activationId}));
    }

    invoke(notificationCenter(), "deliverNotification:", notification);
  }

  private void notificationActivated(@NotNull ID notification) {
    String activationId = activationId(notification);
    Runnable callback = activationId == null ? null : myCallbacksByActivationId.remove(activationId);
    cleanupDeliveredNotifications();
    if (callback != null) {
      ApplicationManager.getApplication().invokeLater(callback);
    }
  }

  private void cleanupDeliveredNotifications() {
    myCallbacksByActivationId.clear();
    removeDeliveredNotifications();
  }

  private static void removeDeliveredNotifications() {
    invoke(notificationCenter(), "removeAllDeliveredNotifications");
  }

  private static @Nullable String activationId(@NotNull ID notification) {
    ID userInfo = invoke(notification, "userInfo");
    if (ID.NIL.equals(userInfo)) {
      return null;
    }
    return Foundation.toStringViaUTF8(invoke(userInfo, "objectForKey:", nsString(ACTIVATION_ID_USER_INFO_KEY)));
  }

  private static @NotNull ID notificationCenter() {
    return invoke(Foundation.getObjcClass("NSUserNotificationCenter"), "defaultUserNotificationCenter");
  }

  private static @NotNull ID createDelegate(@NotNull Callback activationCallback) {
    ID delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), NOTIFICATION_DELEGATE_CLASS_NAME);
    if (!ID.NIL.equals(delegateClass)) {
      ID delegateProtocol = Foundation.getProtocol("NSUserNotificationCenterDelegate");
      if (!ID.NIL.equals(delegateProtocol)) {
        Foundation.addProtocol(delegateClass, delegateProtocol);
      }
      Foundation.addMethod(
        delegateClass,
        Foundation.createSelector("userNotificationCenter:didActivateNotification:"),
        activationCallback,
        "v@:@@"
      );
      Foundation.registerObjcClassPair(delegateClass);
    }
    return invoke(NOTIFICATION_DELEGATE_CLASS_NAME, "new");
  }
}
