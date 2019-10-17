package com.intellij.jps.cache.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;

public class JpsLoaderNotifications {
  public static final NotificationGroup STICKY_NOTIFICATION_GROUP = new NotificationGroup("Compile Output Loader",
                                                                                    NotificationDisplayType.STICKY_BALLOON, true);
  public static final NotificationGroup NONE_NOTIFICATION_GROUP = new NotificationGroup("Compile Output Loader Status",
                                                                                    NotificationDisplayType.NONE, true);
}
