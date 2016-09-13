/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.internal.statistic.persistence;

import com.android.annotations.NonNull;
import com.android.tools.analytics.AnalyticsPublisher;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.intellij.concurrency.JobScheduler;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.configurable.SendPeriod;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@State(
  name = "UsagesStatistic",
  storages = @Storage(value = "usage.statistics.xml", roamingType = RoamingType.DISABLED)
)
public class UsageStatisticsPersistenceComponent extends BasicSentUsagesPersistenceComponent
  implements NamedComponent, PersistentStateComponent<Element> {

  @NonNls private boolean isAllowed = false;
  @NonNls private boolean isShowNotification = true;
  @NotNull private SendPeriod myPeriod = SendPeriod.WEEKLY;

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
    List groupsList = element.getChildren(GROUP_TAG);
    for (Object project : groupsList) {
      Element groupElement = (Element)project;
      String groupId = groupElement.getAttributeValue(GROUP_ID_ATTR);
      double groupPriority = getPriority(groupElement.getAttributeValue(GROUP_PRIORITY_ATTR));

      String valueData = groupElement.getAttributeValue(DATA_ATTR);
      if (!StringUtil.isEmptyOrSpaces(groupId) && !StringUtil.isEmptyOrSpaces(valueData)) {
        try {
          getSentUsages().putAll(ConvertUsagesUtil.convertValueString(GroupDescriptor.create(groupId, groupPriority), valueData));
        } catch (AssertionError e) {
          //don't load incorrect groups
        }
      }
    }

    try {
      setSentTime(Long.parseLong(element.getAttributeValue(LAST_TIME_ATTR)));
    }
    catch (NumberFormatException e) {
      setSentTime(0);
    }

    final String isAllowedValue = element.getAttributeValue(IS_ALLOWED_ATTR);
    setAllowed(!StringUtil.isEmptyOrSpaces(isAllowedValue) && Boolean.parseBoolean(isAllowedValue));

    final String isShowNotificationValue = element.getAttributeValue(SHOW_NOTIFICATION_ATTR);
    setShowNotification(StringUtil.isEmptyOrSpaces(isShowNotificationValue) || Boolean.parseBoolean(isShowNotificationValue));

    setPeriod(parsePeriod(element.getAttributeValue(PERIOD_ATTR)));
  }

  @Override
  public Element getState() {
    Element element = new Element("state");

    for (Map.Entry<GroupDescriptor, Set<UsageDescriptor>> entry : ConvertUsagesUtil.sortDescriptorsByPriority(getSentUsages())
      .entrySet()) {
      Element projectElement = new Element(GROUP_TAG);
      projectElement.setAttribute(GROUP_ID_ATTR, entry.getKey().getId());
      projectElement.setAttribute(GROUP_PRIORITY_ATTR, Double.toString(entry.getKey().getPriority()));
      projectElement.setAttribute(DATA_ATTR, ConvertUsagesUtil.convertValueMap(entry.getValue()));

      element.addContent(projectElement);
    }

    element.setAttribute(LAST_TIME_ATTR, String.valueOf(getLastTimeSent()));
    element.setAttribute(IS_ALLOWED_ATTR, String.valueOf(isAllowed()));
    element.setAttribute(SHOW_NOTIFICATION_ATTR, String.valueOf(isShowNotification()));
    element.setAttribute(PERIOD_ATTR, myPeriod.getName());

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
    isAllowed = allowed;
    // Android Studio: we need to tell our Android Studio specific logging system whether the user opted-in or not.
    updateAndroidStudioMetrics(allowed);
  }

  private void updateAndroidStudioMetrics(boolean allowed) {
    Logger intelliJLogger = Logger.getInstance("#com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent");
    // Create logger & scheduler based on IntelliJ/ADT helpers.
    ILogger logger = new ILogger() {
      @Override
      public void error(@com.android.annotations.Nullable Throwable t, @com.android.annotations.Nullable String msgFormat, Object... args) {
        intelliJLogger.error(String.format(msgFormat, args), t);
      }

      @Override
      public void warning(@NonNull String msgFormat, Object... args) {
        intelliJLogger.warn(String.format(msgFormat, args));
      }

      @Override
      public void info(@NonNull String msgFormat, Object... args) {
        intelliJLogger.info(String.format(msgFormat, args));
      }

      @Override
      public void verbose(@NonNull String msgFormat, Object... args) {
        info(msgFormat, args);
      }
    };

    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    // Update the settings & tracker based on allowed state, will initialize on first call.
    AnalyticsSettings settings = UsageTracker.updateSettingsAndTracker(allowed, logger, scheduler);

    // Update usage tracker maximums for long-lived process.
    UsageTracker tracker = UsageTracker.getInstance();
    tracker.setMaxJournalTime(10, TimeUnit.MINUTES);
    tracker.setMaxJournalSize(1000);

    ApplicationInfo application = ApplicationInfo.getInstance();

    // Update the publisher based on settings updated above, will initialize on first call.
    AnalyticsPublisher.updatePublisher(logger, settings, scheduler, application.getStrictVersion());
  }

  @Override
  public boolean isAllowed() {
    return isAllowed;
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
