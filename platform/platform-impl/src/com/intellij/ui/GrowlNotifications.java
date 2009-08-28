package com.intellij.ui;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.growl.Growl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author mike
 */
class GrowlNotifications {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.GrowlNotifications");

  private static GrowlNotifications ourNotifications;
  private final Set<String> myNotifications = new TreeSet<String>();
  private Growl myGrowl;

  public GrowlNotifications() {
    this(ApplicationNamesInfo.getInstance().getFullProductName());
  }

  GrowlNotifications(String fullProductName) {
    myGrowl = new Growl(fullProductName);
    register();
  }

  private String[] getAllNotifications() {
    return ArrayUtil.toStringArray(myNotifications);
  }

  public static synchronized GrowlNotifications getNotifications() {
    if (ourNotifications == null) {
      ourNotifications = new GrowlNotifications();
    }

    return ourNotifications;
  }

  public void notify(Set<String> allNotifications, @NotNull String notificationName, String title, String description) {
    if (!myNotifications.equals(allNotifications)) {
      myNotifications.addAll(allNotifications);
      register();
    }

    try {
      myGrowl.notifyGrowlOf(notificationName, title, description);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void register() {
    myGrowl.setAllowedNotifications(getAllNotifications());
    try {
      myGrowl.setDefaultNotifications(getAllNotifications());
    }
    catch (Exception e) {
      LOG.error(e);
    }
    myGrowl.register();
  }
}
