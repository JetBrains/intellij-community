/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
    final ID autoReleasePool = createAutoReleasePool();
    final ID applicationIcon = getApplicationIcon();
    final ID defaultNotifications = Foundation.fillArray(myDefaultNotification);
    final ID allNotifications = Foundation.fillArray(myAllNotifications);
    final ID userDict = Foundation.createDict(
      new String[]{GROWL_APP_NAME, GROWL_APP_ICON, GROWL_DEFAULT_NOTIFICATIONS, GROWL_ALL_NOTIFICATIONS},
      new Object[]{myProductName, applicationIcon, defaultNotifications, allNotifications});
    final ID center = invoke("NSDistributedNotificationCenter", "defaultCenter");
    final Object notificationName = Foundation.nsString(GROWL_APPLICATION_REGISTRATION_NOTIFICATION);
    invoke(center, "postNotificationName:object:userInfo:deliverImmediately:", notificationName, null, userDict, true);
    invoke(autoReleasePool, "release");
  }

  public void notifyGrowlOf(final String notification, final String title, final String description) {
    final ID autoReleasePool = createAutoReleasePool();
    final ID dict = Foundation.createDict(
      new String[]{GROWL_NOTIFICATION_NAME, GROWL_NOTIFICATION_TITLE, GROWL_NOTIFICATION_DESCRIPTION, GROWL_APP_NAME},
      new Object[]{notification, title, description, myProductName});
    final ID center = invoke("NSDistributedNotificationCenter", "defaultCenter");
    final Object notificationName = Foundation.nsString(GROWL_NOTIFICATION);
    invoke(center, "postNotificationName:object:userInfo:deliverImmediately:", notificationName, null, dict, true);
    invoke(autoReleasePool, "release");
  }

  public void setAllowedNotifications(final String[] allNotifications) {
    myAllNotifications = allNotifications;
  }

  public void setDefaultNotifications(final String[] defaultNotification) {
    myDefaultNotification = defaultNotification;
  }

  private static ID createAutoReleasePool() {
    return invoke("NSAutoreleasePool", "new");
  }

  private static ID getApplicationIcon() {
    final ID sharedApp = invoke("NSApplication", "sharedApplication");
    final ID nsImage = invoke(sharedApp, "applicationIconImage");
    return invoke(nsImage, "TIFFRepresentation");
  }

  private static ID invoke(@NotNull final String className, @NotNull final String selector, Object... args) {
    return invoke(Foundation.getObjcClass(className), selector, args);
  }

  private static ID invoke(@NotNull final ID id, @NotNull final String selector, Object... args) {
    return invoke(id, Foundation.createSelector(selector), args);
  }

  private static ID invoke(@NotNull final ID id, @NotNull final Pointer selector, Object... args) {
    return Foundation.invoke(id, selector, args);
  }
}
