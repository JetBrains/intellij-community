// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.metadata;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.intellij.internal.statistic.eventLog.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * <b>Example:</b>
 *
 * <pre>{@code
 * {
 *   "groups": [
 *     {
 *       "id": "actions",
 *       "builds": [],
 *       "versions": [ {"from": "9"} ],
 *       "rules": {
 *         "event_id": [ "{enum:action.invoked}" ],
 *         "event_data": {
 *           "action_id": [ "{util#action}" ],
 *           "context_menu": [ "{enum#boolean}" ],
 *           "parent": [ "{enum#parent}", "{util#class_name}" ]
 *         },
 *         "enums": {
 *           "parent": [ "LineMarkerActionWrapper", "TreeActionWrapper", "MyTreeActionWrapper" ]
 *         }
 *       }
 *     },
 *     {
 *       "id" : "run.configuration.exec",
 *       "builds" : [ {"from" : "191.4811"} ],
 *       "versions" : [ {"from" : "1"} ],
 *       "rules": {
 *         "event_id" : [ "{enum:started|ui.shown|finished}" ],
 *         "event_data": {
 *           "id" : [ "{util#run_config_id}" ]
 *         }
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 */
public final class EventGroupRemoteDescriptors {
  @NotNull
  public final ArrayList<EventGroupRemoteDescriptor> groups = new ArrayList<>();
  @Nullable public GroupRemoteRule rules;
  @Nullable public String version;

  @NotNull
  public static EventGroupRemoteDescriptors create(@Nullable String content) throws EventLogMetadataParseException {
    if (StringUtil.isEmptyOrSpaces(content)) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.EMPTY_CONTENT);
    }

    try {
      EventGroupRemoteDescriptors groups = new GsonBuilder().create().fromJson(content, EventGroupRemoteDescriptors.class);
      if (groups != null) {
        return groups;
      }
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON);
    }
    catch (JsonSyntaxException e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.INVALID_JSON, e);
    }
    catch (Exception e) {
      throw new EventLogMetadataParseException(EventLogMetadataParseException.EventLogMetadataParseErrorType.UNKNOWN, e);
    }
  }

  public static final class EventGroupRemoteDescriptor {
    @Nullable
    public String id;
    @Nullable
    public final ArrayList<GroupBuildRange> builds = new ArrayList<>();
    @Nullable
    public final ArrayList<GroupVersionRange> versions = new ArrayList<>();
    @Nullable
    public GroupRemoteRule rules;
  }

  public static class GroupVersionRange {
    public final String from;
    public final String to;

    public GroupVersionRange(String from, String to) {
      this.from = from;
      this.to = to;
    }
  }

  public static final class GroupRemoteRule {
    @Nullable public Set<String> event_id;
    @Nullable public Map<String, Set<String>> event_data;
    @Nullable public Map<String, Set<String>> enums;
    @Nullable public Map<String, String> regexps;
  }

  public static final class GroupBuildRange {
    public String from;
    public String to;
  }
}
