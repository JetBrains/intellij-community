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
package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class NotificationSettings {
  private final String myGroupId;
  private NotificationDisplayType myDisplayType;
  private boolean myShouldLog;

  public NotificationSettings(String groupId, NotificationDisplayType displayType, boolean shouldLog) {
    myGroupId = groupId;
    myDisplayType = displayType;
    myShouldLog = shouldLog;
  }

  public NotificationSettings(final String groupId, final NotificationDisplayType displayType) {
    this(groupId, displayType, true);
  }

 @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @NotNull
  public NotificationDisplayType getDisplayType() {
    return myDisplayType;
  }

  public boolean isShouldLog() {
    return myShouldLog;
  }

  public void setShouldLog(boolean shouldLog) {
    myShouldLog = shouldLog;
  }

  @Nullable
  public static NotificationSettings load(@NotNull final Element element) {
    final String displayTypeString = element.getAttributeValue("displayType");
    NotificationDisplayType displayType = NotificationDisplayType.BALLOON;
    boolean shouldLog = !"false".equals(element.getAttributeValue("shouldLog"));
    if ("BALLOON_ONLY".equals(displayTypeString)) {
      shouldLog = false;
      displayType = NotificationDisplayType.BALLOON;
    }
    else if (displayTypeString != null) {
      try {
        displayType = NotificationDisplayType.valueOf(displayTypeString.toUpperCase());
      }
      catch (IllegalArgumentException ignored) {
      }
    }

    final String groupId = element.getAttributeValue("groupId");
    return groupId != null ? new NotificationSettings(groupId, displayType, shouldLog) : null;
  }

  @NotNull
  public Element save() {
    final Element result = new Element("notification");

    result.setAttribute("groupId", getGroupId());
    final NotificationDisplayType displayType = getDisplayType();
    if (displayType != NotificationDisplayType.BALLOON) {
      result.setAttribute("displayType", displayType.toString());
    }
    if (!myShouldLog) {
      result.setAttribute("shouldLog", "false");
    }

    return result;
  }

  public void setDisplayType(final NotificationDisplayType displayType) {
    myDisplayType = displayType;
  }
}
