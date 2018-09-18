// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.internal.statistic.persistence;

import com.android.annotations.NonNull;
import com.android.tools.analytics.AnalyticsPublisher;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.internal.statistic.configurable.SendPeriod;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@State(
  name = "UsagesStatistic",
  storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED)
)
public class UsageStatisticsPersistenceComponent extends BasicSentUsagesPersistenceComponent implements PersistentStateComponent<Element> {
  public static final String USAGE_STATISTICS_XML = "usage.statistics.xml";

  @NonNls private boolean isShowNotification = true;
  @NotNull private SendPeriod myPeriod = SendPeriod.DAILY;

  @NonNls private static final String LAST_TIME_ATTR = "time";
  @NonNls private static final String EVENT_LOG_LAST_TIME_ATTR = "event-log-time";
  @NonNls private static final String IS_ALLOWED_ATTR = "allowed";
  @NonNls private static final String SHOW_NOTIFICATION_ATTR = "show-notification";
  private ILogger androidLogger;

  public static UsageStatisticsPersistenceComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(UsageStatisticsPersistenceComponent.class);
  }

  public UsageStatisticsPersistenceComponent() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      isShowNotification = false;
    }
  }

  @Override
  public void loadState(@NotNull final Element element) {
    try {
      setSentTime(Long.parseLong(element.getAttributeValue(LAST_TIME_ATTR, "0")));
    }
    catch (NumberFormatException e) {
      setSentTime(0);
    }

    try {
      setEventLogSentTime(Long.parseLong(element.getAttributeValue(EVENT_LOG_LAST_TIME_ATTR, "0")));
    }
    catch (NumberFormatException e) {
      setEventLogSentTime(0);
    }

    // compatibility: if was previously allowed, transfer the setting to the new place
    final String isAllowedValue = element.getAttributeValue(IS_ALLOWED_ATTR);
    if (!StringUtil.isEmptyOrSpaces(isAllowedValue) && Boolean.parseBoolean(isAllowedValue)) {
      setAllowed(true);
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

    long lastEventLogTimeSent = getEventLogLastTimeSent();
    if (lastEventLogTimeSent > 0) {
      element.setAttribute(EVENT_LOG_LAST_TIME_ATTR, String.valueOf(lastEventLogTimeSent));
    }
    if (!isShowNotification()) {
      element.setAttribute(SHOW_NOTIFICATION_ATTR, "false");
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

  @NotNull
  private static SendPeriod parsePeriod(@Nullable String periodAttrValue) {
    if (SendPeriod.DAILY.getName().equals(periodAttrValue)) return SendPeriod.DAILY;
    if (SendPeriod.MONTHLY.getName().equals(periodAttrValue)) return SendPeriod.MONTHLY;

    return SendPeriod.WEEKLY;
  }

  public void setAllowed(boolean allowed) {
    ConsentOptions.getInstance().setSendingUsageStatsAllowed(allowed);
  }

  public void updateAndroidStudioMetrics() {
    updateAndroidStudioMetrics(ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES);
  }

  private void updateAndroidStudioMetrics(boolean allowed) {

    // Update the settings & tracker based on allowed state, will initialize on first call.
    boolean updated = false;
    try {
        if (allowed == AnalyticsSettings.getOptedIn()) {
          updated = false;
        } else {
          AnalyticsSettings.setOptedIn(allowed);
          AnalyticsSettings.saveSettings();
          updated = true;
        }
    } catch (IOException e) {
      getAndroidLogger().error(e, "Unable to update analytics settings");
    }
    if (updated) {
      initializeAndroidStudioUsageTrackerAndPublisher();
    }
  }

  public void initializeAndroidStudioUsageTrackerAndPublisher() {
    ILogger logger = getAndroidLogger();

    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    AnalyticsSettings.initialize(logger, scheduler);

    try {
      // If AnalyticsSettings and IJ opt-in status disagree, then we assume IJ is correct.
      // This catches cornercases such as manual modifications as well as deal with the
      // incorrect rename of "hasOptedIn" to "optedIn" in some early 3.3 canary builds.
      boolean ijOptedIn = ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES;
      if (AnalyticsSettings.getOptedIn() != ijOptedIn) {
        AnalyticsSettings.setOptedIn(ijOptedIn);
        AnalyticsSettings.saveSettings();
      }
      UsageTracker.initialize(scheduler);
    } catch (Exception e) {
      logger.error(e, "Unable to initialize analytics tracker");
      return;
    }
    // Update usage tracker maximums for long-lived process.
    UsageTracker.setMaxJournalTime(10, TimeUnit.MINUTES);
    UsageTracker.setMaxJournalSize(1000);

    ApplicationInfo application = ApplicationInfo.getInstance();
    AnalyticsPublisher.updatePublisher(logger, scheduler, application.getStrictVersion());
  }

  @Override
  public boolean isAllowed() {
    /* Android Studio: we use our own mechanism
    return ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES;
    */
    return AnalyticsSettings.getOptedIn();
  }

  public void setShowNotification(boolean showNotification) {
    isShowNotification = showNotification;
  }

  @Override
  public boolean isShowNotification() {
    return isShowNotification;
  }

  private ILogger getAndroidLogger() {
    if (androidLogger == null) {
      Logger intelliJLogger = Logger.getInstance("#com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent");
      // Create logger & scheduler based on IntelliJ/ADT helpers.
      androidLogger = new ILogger() {
        @Override
        public void error(@com.android.annotations.Nullable Throwable t,
                          @com.android.annotations.Nullable String msgFormat,
                          Object... args) {
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
    }
    return androidLogger;
  }
}
