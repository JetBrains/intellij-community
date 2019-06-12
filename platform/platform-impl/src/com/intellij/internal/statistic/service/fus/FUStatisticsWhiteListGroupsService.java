// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.service.fus.FUSWhitelist.BuildRange;
import com.intellij.internal.statistic.service.fus.FUSWhitelist.GroupFilterCondition;
import com.intellij.internal.statistic.service.fus.FUSWhitelist.VersionRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map;
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
  private static final Logger LOG =
    Logger.getInstance("com.intellij.internal.statistic.service.whiteList.FUStatisticsWhiteListGroupsService");

  /**
   * @return null if error happened during groups fetching
   */
  @Nullable
  public static FUSWhitelist getApprovedGroups(@NotNull String serviceUrl) {
    final String content = getFUSWhiteListContent(serviceUrl);
    return content != null ? parseApprovedGroups(content) : null;
  }

  @Nullable
  public static String loadWhiteListFromServer(@NotNull EventLogExternalSettingsService settingsService) {
    return getFUSWhiteListContent(settingsService.getWhiteListProductUrl());
  }

  public static long lastModifiedWhitelist(@NotNull EventLogExternalSettingsService settingsService) {
    return lastModifiedWhitelist(settingsService.getWhiteListProductUrl());
  }

  @Nullable
  private static String getFUSWhiteListContent(@Nullable String serviceUrl) {
    if (StringUtil.isEmptyOrSpaces(serviceUrl)) return null;

    String content = null;
    try {
      content = HttpRequests.request(serviceUrl)
        .productNameAsUserAgent()
        .readString(null);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return content;
  }

  private static long lastModifiedWhitelist(@Nullable String serviceUrl) {
    try {
      if (!StringUtil.isEmptyOrSpaces(serviceUrl)) {
        return HttpRequests.head(serviceUrl).
          productNameAsUserAgent().
          connect(r -> r.getConnection().getLastModified());
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return 0;
  }

  @Nullable
  public static WLGroups parseWhiteListContent(@Nullable String content) {
    if (StringUtil.isEmptyOrSpaces(content)) return null;
    WLGroups groups = null;
    try {
      groups = new GsonBuilder().create().fromJson(content, WLGroups.class);
    }
    catch (Exception e) {
      LOG.info(e);
    }
    return groups;
  }

  @VisibleForTesting
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
    final List<BuildRange> buildRanges = builds != null ? map(builds, b -> BuildRange.create(b.from, b.to)) : emptyList();
    final List<VersionRange> versionRanges = versions != null ? map(versions, v -> VersionRange.create(v.from, v.to)) : emptyList();
    return new GroupFilterCondition(buildRanges, versionRanges);
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

    public boolean accepts(BuildNumber current) {
      if (!isValid()) {
        return false;
      }
      final boolean hasBuilds = builds != null && !builds.isEmpty();
      return !hasBuilds || builds.stream().anyMatch(build -> build.contains(current));
    }

    private boolean isValid() {
      final boolean hasBuilds = builds != null && !builds.isEmpty();
      final boolean hasVersions = versions != null && !versions.isEmpty();
      return StringUtil.isNotEmpty(id) && (hasBuilds || hasVersions);
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

    public boolean contains(BuildNumber build) {
      return (StringUtil.isEmpty(to) || BuildNumber.fromString(to).compareTo(build) > 0) &&
             (StringUtil.isEmpty(from) || BuildNumber.fromString(from).compareTo(build) <= 0);
    }
  }
}
