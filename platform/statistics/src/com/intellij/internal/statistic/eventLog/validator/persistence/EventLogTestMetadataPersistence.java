// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.eventLog.whitelist.GroupValidationTestRule;
import com.intellij.internal.statistic.service.fus.EventLogMetadataParseException;
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
import java.util.*;

public class EventLogTestMetadataPersistence extends BaseEventLogMetadataPersistence {
  private static final Logger LOG = Logger.getInstance(EventLogTestMetadataPersistence.class);

  public static final String TEST_RULE = "{util#fus_test_mode}";

  private static final String DEPRECATED_TEST_EVENTS_SCHEME_FILE = "test-white-list.json";
  private static final String TEST_EVENTS_SCHEME_FILE = "test-events-scheme.json";

  @NotNull
  private final String myRecorderId;

  public EventLogTestMetadataPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  @Nullable
  public String getCachedEventsScheme() {
    try {
      final File file = getEventsTestSchemeFile();
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
      FileUtil.delete(getEventsTestSchemeFile());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public static WLGroup createGroupWithCustomRules(@NotNull String groupId, @NotNull String rules) {
    final String content =
      "{\"id\":\"" + groupId + "\"," +
      "\"versions\":[ {\"from\" : \"1\"}]," +
      "\"rules\":" + rules + "}";
    return new GsonBuilder().create().fromJson(content, WLGroup.class);
  }

  public static void addTestGroup(@NotNull String recorderId, @NotNull GroupValidationTestRule group) throws IOException {
    String groupId = group.getGroupId();
    WLGroup whitelistGroup = group.getUseCustomRules()
                             ? createGroupWithCustomRules(groupId, group.getCustomRules())
                             : createTestGroup(groupId, Collections.emptySet());
    addNewGroup(recorderId, whitelistGroup);
  }

  private static void addNewGroup(@NotNull String recorderId,
                                  @NotNull WLGroup group) throws IOException {
    final EventLogTestMetadataPersistence persistence = new EventLogTestMetadataPersistence(recorderId);
    final WLGroups whitelist = loadCachedEventGroupsSchemes(persistence);

    saveNewGroup(group, whitelist, persistence.getEventsTestSchemeFile());
  }

  public static void saveNewGroup(@NotNull WLGroup group,
                                   @NotNull WLGroups whitelist,
                                   @NotNull File file) throws IOException {
    whitelist.groups.stream().
      filter(g -> StringUtil.equals(g.id, group.id)).findFirst().
      ifPresent(whitelist.groups::remove);
    whitelist.groups.add(group);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    FileUtil.writeToFile(file, gson.toJson(whitelist));
  }

  @NotNull
  public static WLGroups loadCachedEventGroupsSchemes(@NotNull BaseEventLogMetadataPersistence persistence) {
    final String existing = persistence.getCachedEventsScheme();
    if (StringUtil.isNotEmpty(existing)) {
      try {
        return FUStatisticsWhiteListGroupsService.parseWhiteListContent(existing);
      }
      catch (EventLogMetadataParseException e) {
        LOG.warn("Failed parsing test whitelist", e);
      }
    }
    return new WLGroups();
  }

  @NotNull
  public static WLGroup createTestGroup(@NotNull String groupId, @NotNull Set<String> eventData) {
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
  public File getEventsTestSchemeFile() throws IOException {
    return getDefaultMetadataFile(myRecorderId, TEST_EVENTS_SCHEME_FILE, DEPRECATED_TEST_EVENTS_SCHEME_FILE);
  }

  public void updateTestGroups(@NotNull List<GroupValidationTestRule> groups) throws IOException {
    WLGroups whitelist = new WLGroups();
    for (GroupValidationTestRule group : groups) {
      String groupId = group.getGroupId();
      if (group.getUseCustomRules()) {
        whitelist.groups.add(createGroupWithCustomRules(groupId, group.getCustomRules()));
      }
      else {
        whitelist.groups.add(createTestGroup(groupId, Collections.emptySet()));
      }
    }
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    FileUtil.writeToFile(getEventsTestSchemeFile(), gson.toJson(whitelist));
  }
}
