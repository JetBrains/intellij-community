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
public final class NotificationSettings {
  private final String myGroupId;
  private final NotificationDisplayType myDisplayType;
  private final boolean myShouldLog;
  private final boolean myShouldReadAloud;

  public NotificationSettings(String groupId, NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud) {
    myGroupId = groupId;
    myDisplayType = displayType;
    myShouldLog = shouldLog;
    myShouldReadAloud = shouldReadAloud;
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

  public NotificationSettings withShouldLog(boolean shouldLog) {
    return new NotificationSettings(myGroupId, myDisplayType, shouldLog, myShouldReadAloud);
  }

  public boolean isShouldReadAloud() {
    return myShouldReadAloud;
  }

  public NotificationSettings withShouldReadAloud(boolean shouldReadAloud) {
    return new NotificationSettings(myGroupId, myDisplayType, myShouldLog, shouldReadAloud);
  }

  public NotificationSettings withDisplayType(NotificationDisplayType displayType) {
    return new NotificationSettings(myGroupId, displayType, myShouldLog, myShouldReadAloud);
  }

  @Nullable
  public static NotificationSettings load(@NotNull final Element element) {
    final String displayTypeString = element.getAttributeValue("displayType");
    NotificationDisplayType displayType = NotificationDisplayType.BALLOON;
    boolean shouldLog = !"false".equals(element.getAttributeValue("shouldLog"));
    boolean shouldReadAloud = "true".equals(element.getAttributeValue("shouldReadAloud"));
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
    return groupId != null ? new NotificationSettings(groupId, displayType, shouldLog, shouldReadAloud) : null;
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
    if (myShouldReadAloud) {
      result.setAttribute("shouldReadAloud", "true");
    }

    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NotificationSettings)) return false;

    NotificationSettings settings = (NotificationSettings)o;

    if (myShouldLog != settings.myShouldLog) return false;
    if (myShouldReadAloud != settings.myShouldReadAloud) return false;
    if (myDisplayType != settings.myDisplayType) return false;
    if (!myGroupId.equals(settings.myGroupId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId.hashCode();
    result = 31 * result + myDisplayType.hashCode();
    result = 31 * result + (myShouldLog ? 1 : 0);
    result = 31 * result + (myShouldReadAloud ? 1 : 0);
    return result;
  }
}
