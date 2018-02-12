// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.old;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.service.ConfigurableStatisticsService;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUploadAssistant.LOCK;

@Deprecated  // to be removed in 2018.1x
public class OldConfigurableStatisticsService extends
                                              ConfigurableStatisticsService<StatisticsConnectionService> {

  private static final Logger LOG = Logger.getInstance(OldConfigurableStatisticsService.class);
  private final StatisticsConnectionService connectionService = new OldStatisticsConnectionService();

  @NotNull
  @Override
  public String sendData() {
    String url = connectionService.getServiceUrl();
    Map<String, Set<UsageDescriptor>> content = getContentToSend();

    try {
      HttpConfigurable.getInstance().prepareURL(url);
      String usages = ConvertUsagesUtil.convertUsages(content);
      Response response = Request.Post(url).bodyForm(Form.form().
                                                       add("content", usages).
                                                           add("uuid", PermanentInstallationID.get()).
                                                           add("patch", String.valueOf(false)).
                                                           add("ide", ApplicationNamesInfo.getInstance().getProductName()).build(),
                                                     Consts.UTF_8).execute();

      final HttpResponse httpResponse = response.returnResponse();
      if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        throw new StatServiceException("Error during data sending... Code: " + httpResponse.getStatusLine().getStatusCode());
      }

      final Header errors = httpResponse.getFirstHeader("errors");
      if (errors != null) {
        String value = errors.getValue();
        throw new StatServiceException("Error during updating statistics " + (!StringUtil.isEmptyOrSpaces(value) ? " : " + value : ""));
      }
      return usages;
    }
    catch (StatServiceException e) {
      throw e;
    }
    catch (Exception e) {
      throw new StatServiceException("Error during data sending...", e);
    }
  }

  @NotNull
  public Map<String, Set<UsageDescriptor>> getContentToSend() {
    synchronized (LOCK) {
      Map<String, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (UsagesCollector usagesCollector : UsagesCollector.EP_NAME.getExtensions()) {
        String groupDescriptor = usagesCollector.getGroupId().getId();
        try {
          usageDescriptors.merge(groupDescriptor, usagesCollector.getUsages(), ContainerUtil::union);
        }
        catch (CollectUsagesException e) {
          LOG.info(e);
        }
      }

      return usageDescriptors;
    }
  }

  @Override
  public StatisticsConnectionService getConnectionService() {
    return connectionService;
  }
}
