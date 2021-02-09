// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors.EventGroupRemoteDescriptor;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors.GroupRemoteRule;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataParseException;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils;
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public final class EventLogTestMetadataPersistence extends BaseEventLogMetadataPersistence {
  private static final Logger LOG = Logger.getInstance(EventLogTestMetadataPersistence.class);

  public static final String TEST_RULE = "{util#fus_test_mode}";

  private static final String DEPRECATED_TEST_EVENTS_SCHEME_FILE = "test-white-list.json";
  private static final String TEST_EVENTS_SCHEME_FILE = "test-events-scheme.json";

  private final @NotNull String myRecorderId;

  public EventLogTestMetadataPersistence(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  public @Nullable String getCachedEventsScheme() {
    try {
      Path file = getEventsTestSchemeFile();
      return Files.readString(file);
    }
    catch (NoSuchFileException ignored) {
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  public void cleanup() {
    try {
      Files.deleteIfExists(getEventsTestSchemeFile());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static @NotNull EventGroupRemoteDescriptor createGroupWithCustomRules(@NotNull String groupId, @NotNull String rules) {
    final String content =
      "{\"id\":\"" + groupId + "\"," +
      "\"versions\":[ {\"from\" : \"1\"}]," +
      "\"rules\":" + rules + "}";
    return new GsonBuilder().create().fromJson(content, EventGroupRemoteDescriptor.class);
  }

  public static void addTestGroup(@NotNull String recorderId, @NotNull GroupValidationTestRule group) throws IOException {
    String groupId = group.getGroupId();
    EventGroupRemoteDescriptor groupWithRules = group.getUseCustomRules()
                             ? createGroupWithCustomRules(groupId, group.getCustomRules())
                             : createTestGroup(groupId, Collections.emptySet());
    addNewGroup(recorderId, groupWithRules);
  }

  private static void addNewGroup(@NotNull String recorderId,
                                  @NotNull EventGroupRemoteDescriptor group) throws IOException {
    final EventLogTestMetadataPersistence persistence = new EventLogTestMetadataPersistence(recorderId);
    final EventGroupRemoteDescriptors approvedGroups = loadCachedEventGroupsSchemes(persistence);

    saveNewGroup(group, approvedGroups, persistence.getEventsTestSchemeFile());
  }

  public static void saveNewGroup(@NotNull EventGroupRemoteDescriptor group,
                                  @NotNull EventGroupRemoteDescriptors approvedGroups,
                                  @NotNull Path file) throws IOException {
    List<EventGroupRemoteDescriptor> descriptors = approvedGroups.groups;
    for (EventGroupRemoteDescriptor g : approvedGroups.groups) {
      if (Objects.equals(g.id, group.id)) {
        descriptors.remove(g);
        break;
      }
    }

    approvedGroups.groups.add(group);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Files.createDirectories(file.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      gson.toJson(approvedGroups, EventGroupRemoteDescriptors.class, writer);
    }
  }

  public static @NotNull EventGroupRemoteDescriptors loadCachedEventGroupsSchemes(@NotNull BaseEventLogMetadataPersistence persistence) {
    final String existing = persistence.getCachedEventsScheme();
    if (Strings.isNotEmpty(existing)) {
      try {
        return EventLogMetadataUtils.parseGroupRemoteDescriptors(existing);
      }
      catch (EventLogMetadataParseException e) {
        LOG.warn("Failed parsing test cached events scheme", e);
      }
    }
    return new EventGroupRemoteDescriptors();
  }

  @NotNull
  public static EventGroupRemoteDescriptor createTestGroup(@NotNull String groupId, @NotNull Set<String> eventData) {
    final EventGroupRemoteDescriptor group = new EventGroupRemoteDescriptor();
    group.id = groupId;
    if (group.versions != null) {
      group.versions.add(new EventGroupRemoteDescriptors.GroupVersionRange("1", null));
    }

    GroupRemoteRule rule = new GroupRemoteRule();
    rule.event_id = new HashSet<>(Collections.singletonList(TEST_RULE));

    final Map<String, Set<String>> dataRules = new HashMap<>();
    for (String datum : eventData) {
      dataRules.put(datum, new HashSet<>(Collections.singletonList(TEST_RULE)));
    }
    rule.event_data = dataRules;
    group.rules = rule;
    return group;
  }

  public @NotNull Path getEventsTestSchemeFile() throws IOException {
    return getDefaultMetadataFile(myRecorderId, TEST_EVENTS_SCHEME_FILE, DEPRECATED_TEST_EVENTS_SCHEME_FILE);
  }

  public void updateTestGroups(@NotNull List<GroupValidationTestRule> groups) throws IOException {
    EventGroupRemoteDescriptors approvedGroups = new EventGroupRemoteDescriptors();
    for (GroupValidationTestRule group : groups) {
      String groupId = group.getGroupId();
      if (group.getUseCustomRules()) {
        approvedGroups.groups.add(createGroupWithCustomRules(groupId, group.getCustomRules()));
      }
      else {
        approvedGroups.groups.add(createTestGroup(groupId, Collections.emptySet()));
      }
    }

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Path file = getEventsTestSchemeFile();
    Files.createDirectories(file.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      gson.toJson(approvedGroups, EventGroupRemoteDescriptors.class, writer);
    }
  }
}
