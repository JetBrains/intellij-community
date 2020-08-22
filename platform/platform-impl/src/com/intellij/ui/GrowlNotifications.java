// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.growl.Growl;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

final class GrowlNotifications implements SystemNotificationsImpl.Notifier {
  private static final Logger LOG = Logger.getInstance(GrowlNotifications.class);

  private static GrowlNotifications ourNotifications;

  public static synchronized GrowlNotifications getInstance() {
    if (ourNotifications == null && JnaLoader.isLoaded()) {
      ourNotifications = new GrowlNotifications();
    }
    return ourNotifications;
  }

  private final Growl myGrowl;
  private final Set<String> myNotifications;

  private GrowlNotifications() {
    myGrowl = new Growl(ApplicationNamesInfo.getInstance().getFullProductName());
    myNotifications = new TreeSet<>();
    register();
  }

  private void register() {
    myGrowl.setAllowedNotifications(ArrayUtilRt.toStringArray(myNotifications));
    myGrowl.setDefaultNotifications(ArrayUtilRt.toStringArray(myNotifications));
    myGrowl.register();
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    try {
      if (myNotifications.add(name)) {
        register();
      }

      myGrowl.notifyGrowlOf(name, title, description);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }
}