package com.intellij.internal.statistic.connect;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class RemotelyConfigurableStatisticsService implements StatisticsService {

  private StatisticsConnectionService myConnectionService;
  private StatisticsDataSender sender;
  private StatisticsUploadAssistant myAssistant;

  public RemotelyConfigurableStatisticsService(@NotNull StatisticsConnectionService connectionService,
                                               @NotNull StatisticsDataSender sender,
                                               @NotNull StatisticsUploadAssistant assistant) {
    myConnectionService = connectionService;
    this.sender = sender;
    myAssistant = assistant;
  }

  @Override
  public StatisticsResult send() {
    final String serviceUrl = myConnectionService.getServiceUrl();

    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR");
    }

    if (!myConnectionService.isTransmissionPermitted()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    String content = myAssistant.getData(myConnectionService.getDisabledGroups());

    if (StringUtil.isEmptyOrSpaces(content)) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "NOTHING_TO_SEND");
    }

    try {
      sender.send(serviceUrl, content);
      StatisticsUploadAssistant.updateSentTime();

      return new StatisticsResult(StatisticsResult.ResultCode.SEND, content);
    }
    catch (Exception e) {
      return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, e.getMessage() != null ? e.getMessage() : "NPE");
    }
  }


  @Override
  public Notification createNotification(@NotNull final String groupDisplayId, @Nullable NotificationListener listener) {
    return new StatisticsNotification(groupDisplayId, listener);
  }

  @Nullable
  @Override
  public Map<String, String> getStatisticsConfigurationLabels() {
    // Use defaults
    return null;
  }
}
