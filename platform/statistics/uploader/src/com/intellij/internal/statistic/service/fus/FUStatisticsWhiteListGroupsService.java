// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.StatisticsEventLogUtil;
import com.intellij.internal.statistic.eventLog.EventLogBuildNumber;
import com.intellij.internal.statistic.eventLog.EventLogUploadSettingsService;
import com.intellij.internal.statistic.service.fus.FUSWhitelist.BuildRange;
import com.intellij.internal.statistic.service.fus.FUSWhitelist.GroupFilterCondition;
import com.intellij.internal.statistic.service.fus.FUSWhitelist.VersionRange;
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
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

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
public class FUStatisticsWhiteListGroupsService {
  //private static final Logger LOG = Logger.getInstance(FUStatisticsWhiteListGroupsService.class);

  /**
   * @return null if error happened during groups fetching
   */
  @Nullable
  public static FUSWhitelist getApprovedGroups(@NotNull String userAgent, @NotNull String serviceUrl) {
    final String content = getFUSWhiteListContent(userAgent, serviceUrl);
    return content != null ? parseApprovedGroups(content) : null;
  }

  @Nullable
  public static String loadWhiteListFromServer(@NotNull EventLogUploadSettingsService settingsService) {
    String userAgent = settingsService.getApplicationInfo().getUserAgent();
    return getFUSWhiteListContent(userAgent, settingsService.getWhiteListProductUrl());
  }

  public static long lastModifiedWhitelist(@NotNull EventLogUploadSettingsService settingsService) {
    String userAgent = settingsService.getApplicationInfo().getUserAgent();
    return lastModifiedWhitelist(userAgent, settingsService.getWhiteListProductUrl());
  }

  @Nullable
  private static String getFUSWhiteListContent(@NotNull String userAgent, @Nullable String serviceUrl) {
    if (StatisticsEventLogUtil.isEmptyOrSpaces(serviceUrl)) return null;

    String content = null;
    try (CloseableHttpClient client = StatisticsEventLogUtil.create(userAgent);
         CloseableHttpResponse response = client.execute(new HttpGet(serviceUrl))) {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        content = EntityUtils.toString(entity, StatisticsEventLogUtil.UTF8);
      }
    }
    catch (IOException e) {
      //LOG.info(e);
    }
    return content;
  }

  private static long lastModifiedWhitelist(@NotNull String userAgent, @Nullable String serviceUrl) {
    if (!StatisticsEventLogUtil.isEmptyOrSpaces(serviceUrl)) {
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

  @Nullable
  public static WLGroups parseWhiteListContent(@Nullable String content) {
    if (StatisticsEventLogUtil.isEmptyOrSpaces(content)) return null;
    WLGroups groups = null;
    try {
      groups = new GsonBuilder().create().fromJson(content, WLGroups.class);
    }
    catch (Exception e) {
      //LOG.info(e);
    }
    return groups;
  }

  @NotNull
  public static FUSWhitelist parseApprovedGroups(@Nullable String content) {
    final WLGroups groups = parseWhiteListContent(content);
    if (groups == null) {
      return FUSWhitelist.empty();
    }

    final Map<String, GroupFilterCondition> groupToCondition = new HashMap<>();
    for (WLGroup group : groups.groups) {
      if (group.isValid()) {
        groupToCondition.put(group.id, toCondition(group.builds, group.versions));
      }
    }
    return FUSWhitelist.create(groupToCondition);
  }

  @NotNull
  private static GroupFilterCondition toCondition(@Nullable List<WLBuild> builds, @Nullable List<WLVersion> versions) {
    final List<BuildRange> buildRanges = builds != null && !builds.isEmpty() ? toBuildRanges(builds) : emptyList();
    final List<VersionRange> versionRanges = versions != null && !versions.isEmpty() ? toVersionRanges(versions) : emptyList();
    return new GroupFilterCondition(buildRanges, versionRanges);
  }

  private static List<BuildRange> toBuildRanges(@NotNull List<WLBuild> builds) {
    List<BuildRange> result = new ArrayList<>();
    for (WLBuild build : builds) {
      result.add(BuildRange.create(build.from, build.to));
    }
    return result;
  }

  private static List<VersionRange> toVersionRanges(@NotNull List<WLVersion> versions) {
    List<VersionRange> result = new ArrayList<>();
    for (WLVersion version : versions) {
      result.add(VersionRange.create(version.from, version.to));
    }
    return result;
  }

  public static class WLGroups {
    @NotNull
    public final ArrayList<WLGroup> groups = new ArrayList<>();
    @Nullable public Map<String, Set<String>> globalEnums;
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

    public boolean accepts(EventLogBuildNumber current) {
      if (!isValid()) {
        return false;
      }
      final boolean hasBuilds = builds != null && !builds.isEmpty();
      return !hasBuilds || builds.stream().anyMatch(build -> build.contains(current));
    }

    private boolean isValid() {
      final boolean hasBuilds = builds != null && !builds.isEmpty();
      final boolean hasVersions = versions != null && !versions.isEmpty();
      return StatisticsEventLogUtil.isNotEmpty(id) && (hasBuilds || hasVersions);
    }
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

  private static class WLBuild {
    public String from;
    public String to;

    public boolean contains(EventLogBuildNumber build) {
      //TODO: check build number is not null
      return (StatisticsEventLogUtil.isEmpty(to) || EventLogBuildNumber.fromString(to).compareTo(build) > 0) &&
             (StatisticsEventLogUtil.isEmpty(from) || EventLogBuildNumber.fromString(from).compareTo(build) <= 0);
    }
  }
}
