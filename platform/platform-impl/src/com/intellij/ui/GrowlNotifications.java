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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.mac.growl.Growl;
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
