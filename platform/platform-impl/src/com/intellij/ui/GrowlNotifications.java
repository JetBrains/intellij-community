/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
class GrowlNotifications implements SystemNotificationsImpl.Notifier {
  private static final Logger LOG = Logger.getInstance(GrowlNotifications.class);

  private static GrowlNotifications ourNotifications;

  public static synchronized GrowlNotifications getInstance() {
    if (ourNotifications == null) {
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
    myGrowl.setAllowedNotifications(ArrayUtil.toStringArray(myNotifications));
    myGrowl.setDefaultNotifications(ArrayUtil.toStringArray(myNotifications));
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
