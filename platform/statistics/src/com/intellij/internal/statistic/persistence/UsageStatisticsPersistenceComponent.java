// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.persistence;

import com.android.tools.analytics.AnalyticsPublisher;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.internal.statistic.configurable.SendPeriod;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
  private ILogger androidLogger;

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
      final ConsentOptionsProvider options = getConsentOptionsProvider();
      if (options != null) {
        options.setSendingUsageStatsAllowed(true);
      }
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
    final ConsentOptionsProvider options = getConsentOptionsProvider();
    if (options != null) {
      if (options.isEAP()) {
        isAllowedForEAP = allowed;
      }
      else {
        options.setSendingUsageStatsAllowed(allowed);
      }
    }
    // Android Studio: we need to tell our Android Studio specific logging system whether the user opted-in or not.
    updateAndroidStudioMetrics(allowed);
  }

  public void updateAndroidStudioMetrics() {
    updateAndroidStudioMetrics(ConsentOptions.getInstance().isSendingUsageStatsAllowed() == ConsentOptions.Permission.YES);
  }

  private void updateAndroidStudioMetrics(boolean allowed) {
    ILogger logger = getAndroidLogger();
    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    // Update the settings & tracker based on allowed state, will initialize on first call.
    UsageTracker.updateSettingsAndTracker(allowed, logger, scheduler);

    // Update usage tracker maximums for long-lived process.
    UsageTracker.setMaxJournalTime(10, TimeUnit.MINUTES);
    UsageTracker.setMaxJournalSize(1000);

    ApplicationInfo application = ApplicationInfo.getInstance();

    // Update the publisher based on settings updated above, will initialize on first call.
    AnalyticsPublisher.updatePublisher(logger, scheduler, application.getStrictVersion());
  }

  public boolean isAllowed() {
    /* Android Studio: we use our own mechanism
    final ConsentOptionsProvider options = getConsentOptionsProvider();
    if (options == null) {
      return false;
    }
    return options.isEAP() ? isAllowedForEAP : options.isSendingUsageStatsAllowed();
    */
    return AnalyticsSettings.getInstance(getAndroidLogger()).getOptedIn();
  }

  public void setShowNotification(boolean showNotification) {
    isShowNotification = showNotification;
  }

  public boolean isShowNotification() {
    return isShowNotification && !ApplicationManager.getApplication().isInternal();
  }

  @Nullable
  private static ConsentOptionsProvider getConsentOptionsProvider() {
    return ServiceManager.getService(ConsentOptionsProvider.class);
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
        public void warning(String msgFormat, Object... args) {
          intelliJLogger.warn(String.format(msgFormat, args));
        }

        @Override
        public void info(String msgFormat, Object... args) {
          intelliJLogger.info(String.format(msgFormat, args));
        }

        @Override
        public void verbose(String msgFormat, Object... args) {
          info(msgFormat, args);
        }
      };
    }
    return androidLogger;
  }
}