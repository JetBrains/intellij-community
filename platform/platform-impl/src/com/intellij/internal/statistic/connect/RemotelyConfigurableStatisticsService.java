package com.intellij.internal.statistic.connect;

import com.intellij.internal.statistic.StatisticsUploadAssistant;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

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

  public StatisticsResult send() {
    final String serviceUrl = myConnectionService.getServiceUrl();

    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR");
    }

    if (!myConnectionService.isTransmissionPermitted()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED, "NOT_PERMITTED");
    }

    String content = myAssistant.getData();

    if (StringUtil.isEmptyOrSpaces(content)) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "NOTHING_TO_SEND");
    }

    try {
      sender.send(serviceUrl, content);
      myAssistant.persistSentPatch(content);

      return new StatisticsResult(StatisticsResult.ResultCode.SEND, "SUCCESS");
    }
    catch (Exception e) {
      return new StatisticsResult(StatisticsResult.ResultCode.SEND_WITH_ERRORS, e.getMessage() != null ? e.getMessage() : "NPE");
    }
  }
}
