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
  private String myGroupId;

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
