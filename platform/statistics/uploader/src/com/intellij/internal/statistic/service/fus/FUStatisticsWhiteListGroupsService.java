// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static com.intellij.internal.statistic.StatisticsStringUtil.isEmptyOrSpaces;

/**
 * <ol>
 * <li> Approved collectors could be requested online.
 * <li> This service ({@link FUStatisticsWhiteListGroupsService}) connects to online JB service and requests "approved" UsagesCollectors(groups).
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
public final class FUStatisticsWhiteListGroupsService {

  @NotNull
  public static WLGroups parseWhiteListContent(@Nullable String content) throws EventLogWhitelistParseException {
    if (isEmptyOrSpaces(content)) {
      throw new EventLogWhitelistParseException(EventLogWhitelistParseException.EventLogWhitelistParseErrorType.EMPTY_CONTENT);
    }

    try {
      WLGroups groups = new GsonBuilder().create().fromJson(content, WLGroups.class);
      if (groups != null) {
        return groups;
      }
      throw new EventLogWhitelistParseException(EventLogWhitelistParseException.EventLogWhitelistParseErrorType.INVALID_JSON);
    }
    catch (JsonSyntaxException e) {
      throw new EventLogWhitelistParseException(EventLogWhitelistParseException.EventLogWhitelistParseErrorType.INVALID_JSON, e);
    }
    catch (Exception e) {
      throw new EventLogWhitelistParseException(EventLogWhitelistParseException.EventLogWhitelistParseErrorType.UNKNOWN, e);
    }
  }

  public static class WLGroups {
    @NotNull
    public final ArrayList<WLGroup> groups = new ArrayList<>();
    @Nullable public WLRule rules;
    @Nullable public String version;
  }

  public static class WLGroup {
    @Nullable
    public String id;
    @Nullable
    public final ArrayList<WLBuild> builds = new ArrayList<>();
    @Nullable
    public final ArrayList<WLVersion> versions = new ArrayList<>();
    @Nullable
    public WLRule rules;
  }

  public static class WLVersion {
    public final String from;
    public final String to;

    public WLVersion(String from, String to) {
      this.from = from;
      this.to = to;
    }
  }

  public static class WLRule {
    @Nullable public Set<String> event_id;
    @Nullable public Map<String, Set<String>> event_data;
    @Nullable public Map<String, Set<String>> enums;
    @Nullable public Map<String, String> regexps;
  }

  public static class WLBuild {
    public String from;
    public String to;
  }
}
