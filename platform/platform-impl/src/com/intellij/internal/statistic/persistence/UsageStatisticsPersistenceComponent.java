// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.persistence;

import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.internal.statistic.configurable.SendPeriod;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(
  name = "UsagesStatistic",
  storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED)
)
@Service
public final class UsageStatisticsPersistenceComponent implements PersistentStateComponent<Element> {
  public static final String USAGE_STATISTICS_XML = "usage.statistics.xml";

  private boolean isAllowedForEAP = true;
  private boolean isShowNotification = true;
  private @NotNull SendPeriod myPeriod = SendPeriod.DAILY;

  private static final String LAST_TIME_ATTR = "time";
  private static final String IS_ALLOWED_ATTR = "allowed";
  private static final String IS_ALLOWED_EAP_ATTR = "allowedEap";
  private static final String SHOW_NOTIFICATION_ATTR = "show-notification";
  private long mySentTime = 0;

  public long getLastTimeSent() {
    return mySentTime;
  }

  public void setSentTime(long time) {
    mySentTime = time;
  }

  public static UsageStatisticsPersistenceComponent getInstance() {
    return ServiceManager.getService(UsageStatisticsPersistenceComponent.class);
  }

  @Override
  public void loadState(@NotNull final Element element) {
    try {
      setSentTime(Long.parseLong(element.getAttributeValue(LAST_TIME_ATTR, "0")));
    }
    catch (NumberFormatException e) {
      setSentTime(0);
    }

    final String isAllowedEapValue = element.getAttributeValue(IS_ALLOWED_EAP_ATTR, "true");
    isAllowedForEAP = StringUtil.isEmptyOrSpaces(isAllowedEapValue) || Boolean.parseBoolean(isAllowedEapValue);

    // compatibility: if was previously allowed, transfer the setting to the new place
    final String isAllowedValue = element.getAttributeValue(IS_ALLOWED_ATTR);
    if (!StringUtil.isEmptyOrSpaces(isAllowedValue) && Boolean.parseBoolean(isAllowedValue)) {
      ConsentOptions.getInstance().setSendingUsageStatsAllowed(true);
    }

    final String isShowNotificationValue = element.getAttributeValue(SHOW_NOTIFICATION_ATTR);
    setShowNotification(StringUtil.isEmptyOrSpaces(isShowNotificationValue) || Boolean.parseBoolean(isShowNotificationValue));
  }

  @Override
  public Element getState() {
    Element element = new Element("state");

    long lastTimeSent = getLastTimeSent();
    if (lastTimeSent > 0) {
      element.setAttribute(LAST_TIME_ATTR, String.valueOf(lastTimeSent));
    }

    if (!isShowNotification()) {
      element.setAttribute(SHOW_NOTIFICATION_ATTR, "false");
    }

    if (!isAllowedForEAP) {
      element.setAttribute(IS_ALLOWED_EAP_ATTR, "false");
    }
    return element;
  }

  @NotNull
  public SendPeriod getPeriod() {
    return myPeriod;
  }

  public void setPeriod(@NotNull SendPeriod period) {
    myPeriod = period;
  }

  public void setAllowed(boolean allowed) {
    final ConsentOptions options = ConsentOptions.getInstance();
    if (options.isEAP()) {
      isAllowedForEAP = allowed;
    }
    else {
      options.setSendingUsageStatsAllowed(allowed);
    }
  }

  public boolean isAllowed() {
    final ConsentOptions options = ConsentOptions.getInstance();
    return options.isEAP() ? isAllowedForEAP : options.isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES;
  }

  public void setShowNotification(boolean showNotification) {
    isShowNotification = showNotification;
  }

  public boolean isShowNotification() {
    return isShowNotification && !ApplicationManager.getApplication().isInternal();
  }
}