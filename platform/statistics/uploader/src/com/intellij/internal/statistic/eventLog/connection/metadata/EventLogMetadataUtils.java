// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.intellij.internal.statistic.config.SerializationHelper;
import com.intellij.internal.statistic.eventLog.EventLogBuild;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException.EventLogMetadataLoadErrorType;
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpRequests;
import com.intellij.internal.statistic.eventLog.connection.request.StatsRequestResult;
import com.intellij.internal.statistic.eventLog.connection.request.StatsResponseException;
import com.jetbrains.fus.reporting.model.dictionaries.RemoteDictionaryList;
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.internal.statistic.config.StatisticsStringUtil.isEmptyOrSpaces;

/**
 * <ol>
 * <li> All collectors/groups have to be requested online.
 * <li> {@link EventLogMetadataUtils#loadMetadataFromServer(String, StatsConnectionSettings)} connects to online JB service
 * and requests approved groups with conditions on product builds, group versions and group scheme.
 *
 * <li> Online JB service  returns  result in json file format as described in {@link EventGroupRemoteDescriptors}:
 * </ol>
 */
public final class EventLogMetadataUtils {

  /**
   * @return empty rules if error happened during groups fetching or parsing
   */
  public static @NotNull EventGroupsFilterRules<EventLogBuild> loadAndParseGroupsFilterRules(@NotNull String serviceUrl, @NotNull StatsConnectionSettings settings) {
    try {
      String content = loadMetadataFromServer(serviceUrl, settings);
      EventGroupRemoteDescriptors groups = parseGroupRemoteDescriptors(content);
      return EventGroupsFilterRules.create(groups, EventLogBuild.EVENT_LOG_BUILD_PRODUCER);
    }
    catch (EventLogMetadataParseException | EventLogMetadataLoadException e) {
      return EventGroupsFilterRules.empty();
    }
  }

  public static @NotNull EventGroupRemoteDescriptors parseGroupRemoteDescriptors(@Nullable String content) throws EventLogMetadataParseException {
    if (isEmptyOrSpaces(content)) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.EMPTY_CONTENT);
    }

    try {
      EventGroupRemoteDescriptors groups = SerializationHelper.INSTANCE.deserialize(content, EventGroupRemoteDescriptors.class);

      if (groups == null) {
        throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON);
      }

      return groups;
    }
    catch (StreamReadException | DatabindException e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON, e);
    }
    catch (Exception e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.UNKNOWN, e);
    }
  }

  public static @NotNull RemoteDictionaryList parseRemoteDictionaryList(@Nullable String content) throws EventLogMetadataParseException {
    if (isEmptyOrSpaces(content)) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.EMPTY_CONTENT);
    }
    try {
      RemoteDictionaryList dictionaryList = SerializationHelper.INSTANCE.deserialize(content, RemoteDictionaryList.class);
      if (dictionaryList == null) {
        throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON);
      }
      return dictionaryList;
    }
    catch (StreamReadException | DatabindException e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON, e);
    }
    catch (Exception e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.UNKNOWN, e);
    }
  }

  public static @NotNull String loadMetadataFromServer(@Nullable String serviceUrl, @NotNull StatsConnectionSettings settings)
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


  public static long lastModifiedMetadata(@Nullable String serviceUrl, @NotNull StatsConnectionSettings settings) {
    if (isEmptyOrSpaces(serviceUrl)) return 0;

    try {
      StatsRequestResult<Long> result = StatsHttpRequests.head(serviceUrl, settings).send(r -> r.lastModified());
      return result.getResult() != null ? result.getResult() : 0L;
    }
    catch (StatsResponseException | IOException e) {
      return 0;
    }
  }

  public static Map<String, Long> dictionariesLastModified(@Nullable String serviceUrl, @NotNull String recorderId, @NotNull StatsConnectionSettings settings)
    throws EventLogMetadataLoadException {
    if (isEmptyOrSpaces(serviceUrl)) {
      throw new EventLogMetadataLoadException(EventLogMetadataLoadErrorType.EMPTY_SERVICE_URL);
    }

    String baseUrl = serviceUrl + recorderId + "/";
    RemoteDictionaryList dictionaryList;
    try {
      StatsRequestResult<String> result = StatsHttpRequests.request( baseUrl + "dictionaries.json", settings).send(r -> r.readAsString());
      if (!result.isSucceed()) {
        throw new EventLogMetadataLoadException(EventLogMetadataLoadErrorType.UNREACHABLE_SERVICE, result.getError());
      }
      dictionaryList = parseRemoteDictionaryList(result.getResult());
    }
    catch (StatsResponseException | IOException | EventLogMetadataParseException e) {
      throw new EventLogMetadataLoadException(EventLogMetadataLoadErrorType.ERROR_ON_LOAD, e);
    }

    Map<String, Long> lastModifiedMap = new HashMap<>();
    for(String dictionary : dictionaryList.getDictionaries()) {
      try {
        StatsRequestResult<Long> result = StatsHttpRequests.head(baseUrl + dictionary, settings).send(r -> r.lastModified());
        lastModifiedMap.put(dictionary, result.getResult());
      }
      catch (StatsResponseException | IOException e) {
        lastModifiedMap.put(dictionary, 0L);
      }
    }
    return lastModifiedMap;
  }

  public static @NotNull String loadDictionaryFromServer(
    @Nullable String serviceUrl,
    @NotNull String recorderId,
    @NotNull String dictionaryName,
    @NotNull StatsConnectionSettings settings
  ) throws EventLogMetadataLoadException {
    return loadMetadataFromServer(serviceUrl + recorderId + "/" + dictionaryName, settings);
  }
}
