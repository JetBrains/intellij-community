// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException.EventLogMetadataLoadErrorType;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException.EventLogMetadataParseErrorType;
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpRequests;
import com.intellij.internal.statistic.eventLog.connection.request.StatsRequestResult;
import com.intellij.internal.statistic.eventLog.connection.request.StatsResponseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.internal.statistic.StatisticsStringUtil.isEmptyOrSpaces;

/**
 * <ol>
 * <li> All collectors/groups have to be requested online.
 * <li> {@link EventLogMetadataUtils#loadMetadataFromServer(String, EventLogConnectionSettings)} connects to online JB service
 * and requests approved groups with conditions on product builds, group versions and group scheme.
 *
 * <li> Online JB service  returns  result in json file format as described in {@link EventGroupRemoteDescriptors}:
 * </ol>
 */
public final class EventLogMetadataUtils {

  /**
   * @return empty rules if error happened during groups fetching or parsing
   */
  @NotNull
  public static EventGroupsFilterRules loadAndParseGroupsFilterRules(@NotNull String serviceUrl, @NotNull EventLogConnectionSettings settings) {
    try {
      String content = loadMetadataFromServer(serviceUrl, settings);
      return parseGroupFilterRules(content);
    }
    catch (EventLogMetadataParseException | EventLogMetadataLoadException e) {
      return EventGroupsFilterRules.empty();
    }
  }

  @NotNull
  public static EventGroupsFilterRules parseGroupFilterRules(@Nullable String content) throws EventLogMetadataParseException {
    EventGroupRemoteDescriptors groups = parseGroupRemoteDescriptors(content);
    Map<String, EventGroupFilterRules> groupToCondition = new HashMap<>();
    for (EventGroupRemoteDescriptors.EventGroupRemoteDescriptor group : groups.groups) {
      groupToCondition.put(group.id, EventGroupFilterRules.create(group));
    }
    return EventGroupsFilterRules.create(groupToCondition);
  }

  @NotNull
  public static EventGroupRemoteDescriptors parseGroupRemoteDescriptors(@Nullable String content) throws EventLogMetadataParseException {
    if (isEmptyOrSpaces(content)) {
      throw new EventLogMetadataParseException(EventLogMetadataParseErrorType.EMPTY_CONTENT);
    }

    try {
      EventGroupRemoteDescriptors groups = new GsonBuilder().create().fromJson(content, EventGroupRemoteDescriptors.class);
      if (groups != null) {
        return groups;
      }
      throw new EventLogMetadataParseException(EventLogMetadataParseErrorType.INVALID_JSON);
    }
    catch (JsonSyntaxException e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseErrorType.INVALID_JSON, e);
    }
    catch (Exception e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseErrorType.UNKNOWN, e);
    }
  }

  @NotNull
  public static String loadMetadataFromServer(@Nullable String serviceUrl, @NotNull EventLogConnectionSettings settings)
    throws EventLogMetadataLoadException {
    if (isEmptyOrSpaces(serviceUrl)) {
      throw new EventLogMetadataLoadException(EventLogMetadataLoadErrorType.EMPTY_SERVICE_URL);
    }

    try {
      StatsRequestResult<String> result = StatsHttpRequests.request(serviceUrl, settings).send(r -> r.readAsString());
      if (result.isSucceed()) {
        return result.getResult();
      }
      throw new EventLogMetadataLoadException(EventLogMetadataLoadErrorType.UNREACHABLE_SERVICE, result.getError());
    }
    catch (StatsResponseException | IOException e) {
      throw new EventLogMetadataLoadException(EventLogMetadataLoadErrorType.ERROR_ON_LOAD, e);
    }
  }

  public static long lastModifiedMetadata(@Nullable String serviceUrl, @NotNull EventLogConnectionSettings settings) {
    if (isEmptyOrSpaces(serviceUrl)) return 0;

    try {
      StatsRequestResult<Long> result = StatsHttpRequests.head(serviceUrl, settings).send(r -> r.lastModified());
      return result.getResult() != null ? result.getResult() : 0L;
    }
    catch (StatsResponseException | IOException e) {
      return 0;
    }
  }
}
