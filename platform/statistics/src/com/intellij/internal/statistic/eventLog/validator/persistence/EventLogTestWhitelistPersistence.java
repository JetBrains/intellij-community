// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroup;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLGroups;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLRule;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService.WLVersion;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EventLogTestWhitelistPersistence extends BaseEventLogWhitelistPersistence {
  private static final Logger LOG =
    Logger.getInstance(EventLogTestWhitelistPersistence.class);

  private static final String TEST_RULE = "{util#fus_test_mode}";
  private static final String TEST_WHITE_LIST_DATA_FILE = "test-white-list.json";
  @NotNull
  private final String myRecorderId;

  public EventLogTestWhitelistPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  @Nullable
  public String getCachedWhitelist() {
    try {
      final File file = getWhitelistFile();
      if (file.exists()) {
        return FileUtil.loadFile(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  public void cleanup() {
    try {
      FileUtil.delete(getWhitelistFile());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static void addGroupWithCustomRules(@NotNull String recorderId, @NotNull String groupId, @NotNull String rules)
    throws IOException {
    final String content =
      "{\"id\":\"" + groupId + "\"," +
      "\"versions\":[ {\"from\" : \"1\"}]," +
      "\"rules\":" + rules + "}";
    final WLGroup newGroup = new GsonBuilder().create().fromJson(content, WLGroup.class);
    addNewGroup(recorderId, newGroup);
  }

  public static void addTestGroup(@NotNull String recorderId, @NotNull String groupId) throws IOException {
    final WLGroup group = createTestGroup(groupId, Collections.emptySet());
    addNewGroup(recorderId, group);
  }

  private static void addNewGroup(@NotNull String recorderId,
                                  @NotNull WLGroup group) throws IOException {
    final EventLogTestWhitelistPersistence persistence = new EventLogTestWhitelistPersistence(recorderId);
    final WLGroups whitelist = loadTestWhitelist(persistence);

    whitelist.groups.stream().
      filter(g -> StringUtil.equals(g.id, group.id)).findFirst().
      ifPresent(whitelist.groups::remove);
    whitelist.groups.add(group);
    final File file = persistence.getWhitelistFile();
    FileUtil.writeToFile(file, new Gson().toJson(whitelist));
  }

  @NotNull
  public static WLGroups loadTestWhitelist(@NotNull BaseEventLogWhitelistPersistence persistence) {
    final String existing = persistence.getCachedWhitelist();
    if (StringUtil.isNotEmpty(existing)) {
      final WLGroups loaded = FUStatisticsWhiteListGroupsService.parseWhiteListContent(existing);
      if (loaded != null) {
        return loaded;
      }
    }
    return new WLGroups();
  }

  @NotNull
  private static WLGroup createTestGroup(@NotNull String groupId, @NotNull Set<String> eventData) {
    final WLGroup group = new WLGroup();
    group.id = groupId;
    if (group.versions != null) {
      group.versions.add(new WLVersion("1", null));
    }

    final WLRule rule = new WLRule();
    rule.event_id = ContainerUtil.newHashSet(TEST_RULE);

    final Map<String, Set<String>> dataRules = new HashMap<>();
    for (String datum : eventData) {
      dataRules.put(datum, ContainerUtil.newHashSet(TEST_RULE));
    }
    rule.event_data = dataRules;
    group.rules = rule;
    return group;
  }

  @NotNull
  public File getWhitelistFile() throws IOException {
    return getDefaultWhitelistFile(myRecorderId, TEST_WHITE_LIST_DATA_FILE);
  }
}
