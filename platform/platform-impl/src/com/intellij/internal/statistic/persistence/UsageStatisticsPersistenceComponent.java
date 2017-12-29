// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.internal.statistic.persistence;

import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.configurable.SendPeriod;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

@State(
  name = "UsagesStatistic",
  storages = @Storage(value = "usage.statistics.xml", roamingType = RoamingType.DISABLED)
)
public class UsageStatisticsPersistenceComponent extends BasicSentUsagesPersistenceComponent
  implements NamedComponent, PersistentStateComponent<Element> {

  @NonNls private boolean isShowNotification = true;
  @NotNull private SendPeriod myPeriod = SendPeriod.DAILY;

  @NonNls private static final String DATA_ATTR = "data";
  @NonNls private static final String GROUP_TAG = "group";
  @NonNls private static final String GROUP_ID_ATTR = "id";
  @NonNls private static final String GROUP_PRIORITY_ATTR = "priority";

  @NonNls private static final String LAST_TIME_ATTR = "time";
  @NonNls private static final String IS_ALLOWED_ATTR = "allowed";
  @NonNls private static final String PERIOD_ATTR = "period";
  @NonNls private static final String SHOW_NOTIFICATION_ATTR = "show-notification";

  public static UsageStatisticsPersistenceComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(UsageStatisticsPersistenceComponent.class);
  }

  public UsageStatisticsPersistenceComponent() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      isShowNotification = false;
    }
  }

  @Override
  public void loadState(final Element element) {
    for (Element groupElement : element.getChildren(GROUP_TAG)) {
      String groupId = groupElement.getAttributeValue(GROUP_ID_ATTR);
      double groupPriority = getPriority(groupElement.getAttributeValue(GROUP_PRIORITY_ATTR));

      String valueData = groupElement.getAttributeValue(DATA_ATTR);
      if (!StringUtil.isEmptyOrSpaces(groupId) && !StringUtil.isEmptyOrSpaces(valueData)) {
        try {
          getSentUsages().putAll(ConvertUsagesUtil.convertValueString(GroupDescriptor.create(groupId, groupPriority), valueData));
        }
        catch (AssertionError e) {
          //don't load incorrect groups
        }
      }
    }

    try {
      setSentTime(Long.parseLong(element.getAttributeValue(LAST_TIME_ATTR, "0")));
    }
    catch (NumberFormatException e) {
      setSentTime(0);
    }

    // compatibility: if was previously allowed, transfer the setting to the new place
    final String isAllowedValue = element.getAttributeValue(IS_ALLOWED_ATTR);
    if (!StringUtil.isEmptyOrSpaces(isAllowedValue) && Boolean.parseBoolean(isAllowedValue)) {
      setAllowed(true);
    }

    final String isShowNotificationValue = element.getAttributeValue(SHOW_NOTIFICATION_ATTR);
    setShowNotification(StringUtil.isEmptyOrSpaces(isShowNotificationValue) || Boolean.parseBoolean(isShowNotificationValue));

    //setPeriod(parsePeriod(element.getAttributeValue(PERIOD_ATTR)));
  }

  @Override
  public Element getState() {
    Element element = new Element("state");

    for (Map.Entry<GroupDescriptor, Set<UsageDescriptor>> entry : ConvertUsagesUtil.sortDescriptorsByPriority(getSentUsages()).entrySet()) {
      Element projectElement = new Element(GROUP_TAG);
      projectElement.setAttribute(GROUP_ID_ATTR, entry.getKey().getId());
      projectElement.setAttribute(GROUP_PRIORITY_ATTR, Double.toString(entry.getKey().getPriority()));
      projectElement.setAttribute(DATA_ATTR, ConvertUsagesUtil.convertValueMap(entry.getValue()));

      element.addContent(projectElement);
    }

    long lastTimeSent = getLastTimeSent();
    if (lastTimeSent > 0) {
      element.setAttribute(LAST_TIME_ATTR, String.valueOf(lastTimeSent));
    }

    //if (isAllowed()) {
    //  element.setAttribute(IS_ALLOWED_ATTR, "true");
    //}
    if (!isShowNotification()) {
      element.setAttribute(SHOW_NOTIFICATION_ATTR, "false");
    }
    //if (myPeriod != SendPeriod.WEEKLY) {
    //  element.setAttribute(PERIOD_ATTR, myPeriod.getName());
    //}

    return element;
  }

  @NotNull
  public SendPeriod getPeriod() {
    return myPeriod;
  }

  public void setPeriod(@NotNull SendPeriod period) {
    myPeriod = period;
  }

  @NotNull
  private static SendPeriod parsePeriod(@Nullable String periodAttrValue) {
    if (SendPeriod.DAILY.getName().equals(periodAttrValue)) return SendPeriod.DAILY;
    if (SendPeriod.MONTHLY.getName().equals(periodAttrValue)) return SendPeriod.MONTHLY;

    return SendPeriod.WEEKLY;
  }

  public void setAllowed(boolean allowed) {
    ConsentOptions.getInstance().setSendingUsageStatsAllowed(allowed);
  }

  @Override
  public boolean isAllowed() {
    return ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES;
  }

  public void setShowNotification(boolean showNotification) {
    isShowNotification = showNotification;
  }

  @Override
  public boolean isShowNotification() {
    return isShowNotification;
  }

  private static double getPriority(String priority) {
    if (StringUtil.isEmptyOrSpaces(priority)) return GroupDescriptor.DEFAULT_PRIORITY;

    return Double.parseDouble(priority);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "SentUsagesPersistenceComponent";
  }
}
