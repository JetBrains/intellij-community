// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import com.intellij.internal.statistic.service.fus.EventLogWhitelistLoadException.EventLogWhitelistLoadErrorType;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroup;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroups;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.internal.statistic.StatisticsStringUtil.isEmptyOrSpaces;

/**
 * <ol>
 * <li> Approved collectors could be requested online.
 * <li> This service ({@link StatisticsWhitelistLoader}) connects to online JB service and requests "approved" UsagesCollectors(groups).
 * <li> Online JB service  returns  result in json file format:
 * <pre>{@code
 * {
 * "groups" : [
 *   {
 *    "id" : "productivity",
 *    "builds" : [{ "from" : "173.4127.37", "to": "182.124" }, { "from" : "183.12" }],
 *    "versions" : [{ "from" : "2", "to": "4" }, { "from" : "7" }]
 *   },
 *   {
 *    "id" : "spring-example"
 *   }
 *  ]
 * }
 * }</pre>
 * </ol>
 */
public class StatisticsWhitelistLoader {

  /**
   * @return empty whitelist if error happened during groups fetching or parsing
   */
  @NotNull
  public static StatisticsWhitelistConditions getApprovedGroups(@NotNull String serviceUrl, @NotNull String userAgent) {
    try {
      String content = loadWhiteListFromServer(serviceUrl, userAgent);
      return parseApprovedGroups(content);
    }
    catch (EventLogWhitelistParseException | EventLogWhitelistLoadException e) {
      return StatisticsWhitelistConditions.empty();
    }
  }

  @NotNull
  public static String loadWhiteListFromServer(@Nullable String serviceUrl, @NotNull String userAgent)
    throws EventLogWhitelistLoadException {
    if (isEmptyOrSpaces(serviceUrl)) {
      throw new EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType.EMPTY_SERVICE_URL);
    }

    try (CloseableHttpClient client = StatisticsEventLogUtil.create(userAgent);
         CloseableHttpResponse response = client.execute(new HttpGet(serviceUrl))) {
      StatusLine statusLine = response.getStatusLine();
      int code = statusLine != null ? statusLine.getStatusCode() : -1;
      if (code != HttpStatus.SC_OK) {
        throw new EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType.UNREACHABLE_SERVICE, code);
      }

      HttpEntity entity = response.getEntity();
      String content = entity != null ? EntityUtils.toString(entity, StatisticsEventLogUtil.UTF8) : null;
      if (content == null) {
        throw new EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType.EMPTY_RESPONSE_BODY);
      }
      return content;
    }
    catch (IOException e) {
      throw new EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType.ERROR_ON_LOAD, e);
    }
  }

  public static long lastModifiedWhitelist(@Nullable String serviceUrl, @NotNull String userAgent) {
    if (!isEmptyOrSpaces(serviceUrl)) {
      try (CloseableHttpClient client = StatisticsEventLogUtil.create(userAgent);
           CloseableHttpResponse response = client.execute(new HttpHead(serviceUrl))) {
        Header[] headers = response.getHeaders(HttpHeaders.LAST_MODIFIED);
        return Stream.of(headers).
          map(header -> header.getValue()).
          filter(Objects::nonNull).
          map(value -> DateUtils.parseDate(value).getTime()).
          max(Long::compareTo).orElse(0L);
      }
      catch (IOException e) {
        //LOG.info(e);
      }
    }
    return 0;
  }

  @NotNull
  public static StatisticsWhitelistConditions parseApprovedGroups(@Nullable String content) throws EventLogWhitelistParseException {
    WLGroups groups = FUStatisticsWhiteListGroupsService.parseWhiteListContent(content);
    Map<String, StatisticsWhitelistGroupConditions> groupToCondition = new HashMap<>();
    for (WLGroup group : groups.groups) {
      groupToCondition.put(group.id, StatisticsWhitelistGroupConditions.create(group));
    }
    return StatisticsWhitelistConditions.create(groupToCondition);
  }
}
