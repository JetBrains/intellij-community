// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <ol>
 * <li> Approved collectors could be requested online.
 * <li> This service ({@link FUStatisticsWhiteListGroupsService}) connects to online JB service and requests "approved" UsagesCollectors(groups).
 * <li> Online JB service  returns  result in json file format:
 * <pre>{@code
 * {
 * "groups" : [
 *   {
 *    "id" : "statistics.Productivity",
 *    "builds" : [{ "from" : "173.4127.37", "to": "182.124" }, { "from" : "183.12" }]
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

  @NotNull
  public static Set<String> getApprovedGroups(@NotNull String serviceUrl, @NotNull BuildNumber current) {
    String content = null;
    try {
      content = HttpRequests.request(serviceUrl)
                            .productNameAsUserAgent()
                            .readString(null);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    if (content == null) return Collections.emptySet();

    return parseApprovedGroups(content, current);
  }

  @VisibleForTesting
  @NotNull
  public static Set<String> parseApprovedGroups(String content, @NotNull BuildNumber build) {
    WLGroups groups = null;
    try {
      groups = new GsonBuilder().create().fromJson(content, WLGroups.class);
    }
    catch (Exception e) {
      LOG.info(e);
    }

    return groups == null ? Collections.emptySet() :
           groups.groups.stream().
             filter(group -> group.accepts(build)).
             map(group -> group.id).collect(Collectors.toSet());
  }

  private static class WLGroups {
    @NotNull
    public final ArrayList<WLGroup> groups = new ArrayList<>();
  }

  private static class WLGroup {
    @NotNull
    public final String id;
    @NotNull
    public final ArrayList<WLBuild> builds = new ArrayList<>();

    WLGroup(@NotNull String id) {
      this.id = id;
    }

    public boolean accepts(BuildNumber current) {
      return builds.stream().anyMatch(build -> build.contains(current));
    }
  }

  private static class WLBuild {
    public final String from;
    public final String to;

    private WLBuild(String from, String to) {
      this.from = from;
      this.to = to;
    }

    public boolean contains(BuildNumber build) {
      return (StringUtil.isEmpty(to) || BuildNumber.fromString(to).compareTo(build) > 0) &&
             (StringUtil.isEmpty(from) || BuildNumber.fromString(from).compareTo(build) <= 0);
    }
  }
}
