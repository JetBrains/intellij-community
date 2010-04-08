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
package com.intellij.ui.mac.growl;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class Growl {
  private static final String GROWL_APPLICATION_REGISTRATION_NOTIFICATION = "GrowlApplicationRegistrationNotification";
  private static final String GROWL_APP_NAME = "ApplicationName";
  private static final String GROWL_APP_ICON = "ApplicationIcon";
  private static final String GROWL_DEFAULT_NOTIFICATIONS = "DefaultNotifications";
  private static final String GROWL_ALL_NOTIFICATIONS = "AllNotifications";
  private static final String GROWL_NOTIFICATION_NAME = "NotificationName";
  private static final String GROWL_NOTIFICATION_TITLE = "NotificationTitle";
  private static final String GROWL_NOTIFICATION_DESCRIPTION = "NotificationDescription";
  private static final String GROWL_NOTIFICATION = "GrowlNotification";

  private final String myProductName;
  private String[] myAllNotifications;
  private String[] myDefaultNotification;

  public Growl(@NotNull final String productName) {
    myProductName = productName;
  }

  public void register() {
    final Pointer autoReleasePool = createAutoReleasePool();
    final Pointer applicationIcon = getApplicationIcon();

    final Pointer defaultNotifications = fillArray(myDefaultNotification);
    final Pointer allNotifications = fillArray(myAllNotifications);

    final Pointer userDict = createDict(new String[]{GROWL_APP_NAME, GROWL_APP_ICON, GROWL_DEFAULT_NOTIFICATIONS, GROWL_ALL_NOTIFICATIONS},
        new Object[]{myProductName, applicationIcon, defaultNotifications, allNotifications});

    final Pointer center = invoke("NSDistributedNotificationCenter", "defaultCenter");
    final Object notificationName = Foundation.cfString(GROWL_APPLICATION_REGISTRATION_NOTIFICATION);
    invoke(center, "postNotificationName:object:userInfo:deliverImmediately:", notificationName, null, userDict, true);

    invoke(autoReleasePool, "release");
  }

  public void notifyGrowlOf(final String notification, final String title, final String description) {
    final Pointer autoReleasePool = createAutoReleasePool();

    final Pointer dict = createDict(new String[]{
        GROWL_NOTIFICATION_NAME, GROWL_NOTIFICATION_TITLE, GROWL_NOTIFICATION_DESCRIPTION, GROWL_APP_NAME},
        new Object[]{notification, title, description, myProductName});
    final Pointer center = invoke("NSDistributedNotificationCenter", "defaultCenter");
    final Object notificationName = Foundation.cfString(GROWL_NOTIFICATION);

    invoke(center, "postNotificationName:object:userInfo:deliverImmediately:", notificationName, null, dict, true);
    invoke(autoReleasePool, "release");
  }

  public void setAllowedNotifications(final String[] allNotifications) {
    myAllNotifications = allNotifications;
  }

  public void setDefaultNotifications(final String[] defaultNotification) {
    myDefaultNotification = defaultNotification;
  }

  private static Pointer createAutoReleasePool() {
    return invoke("NSAutoreleasePool", "new");
  }

  private static Pointer fillArray(final Object[] a) {
    final Pointer result = invoke("NSMutableArray", "array");
    for (Object s : a) {
      invoke(result, "addObject:", convertType(s));
    }

    return result;
  }

  private static Pointer createDict(@NotNull final String[] keys, @NotNull final Object[] values) {
    final Pointer nsKeys = invoke("NSArray", "arrayWithObjects:", convertTypes(keys));
    final Pointer nsData = invoke("NSArray", "arrayWithObjects:", convertTypes(values));

    return invoke("NSDictionary", "dictionaryWithObjects:forKeys:", nsData, nsKeys);
  }

  private static Object convertType(final Object o) {
    if (o instanceof ID) {
      return o;
    } else if (o instanceof String) {
      return Foundation.cfString((String) o);
    } else {
      throw new IllegalArgumentException("Unsupported type! " + o.getClass());
    }
  }

  private static Object[] convertTypes(@NotNull final Object[] v) {
    final Object[] result = new Object[v.length];
    for (int i = 0; i < v.length; i++) {
      result[i] = convertType(v[i]);
    }

    return result;
  }

  private static Pointer getApplicationIcon() {
    final Pointer sharedApp = invoke("NSApplication", "sharedApplication");
    final Pointer nsImage = invoke(sharedApp, "applicationIconImage");
    return invoke(nsImage, "TIFFRepresentation");
  }

  private static Pointer invoke(@NotNull final String className, @NotNull final String selector, Object... args) {
    return invoke(Foundation.getClass(className), selector, args);
  }

  private static Pointer invoke(@NotNull final Pointer id, @NotNull final String selector, Object... args) {
    return invoke(id, Foundation.createSelector(selector), args);
  }

  private static Pointer invoke(@NotNull final Pointer id, @NotNull final Pointer selector, Object... args) {
    return Foundation.invoke(id, selector, args);
  }
}
