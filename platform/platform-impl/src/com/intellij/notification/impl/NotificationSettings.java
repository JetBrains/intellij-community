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

  private NotificationDisplayType myDisplayType;
  private final String myGroupId;

  public NotificationSettings(final String groupId, final NotificationDisplayType displayType) {
    myGroupId = groupId;
    myDisplayType = displayType;
  }

 @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @NotNull
  public NotificationDisplayType getDisplayType() {
    return myDisplayType;
  }

  @Nullable
  public static NotificationSettings load(@NotNull final Element element) {
    final String displayTypeString = element.getAttributeValue("displayType");
    NotificationDisplayType displayType = NotificationDisplayType.BALLOON;
    if (displayTypeString != null) {
      try {
        displayType = NotificationDisplayType.valueOf(displayTypeString.toUpperCase());
      }
      catch (IllegalArgumentException e) {
        displayType = NotificationDisplayType.BALLOON;
      }
    }

    final String groupId = element.getAttributeValue("groupId");
    return groupId != null ? new NotificationSettings(groupId, displayType) : null;
  }

  @NotNull
  public Element save() {
    final Element result = new Element("notification");

    result.setAttribute("groupId", getGroupId());
    result.setAttribute("displayType", getDisplayType().toString());

    return result;
  }

  public void setDisplayType(final NotificationDisplayType displayType) {
    myDisplayType = displayType;
  }
}
