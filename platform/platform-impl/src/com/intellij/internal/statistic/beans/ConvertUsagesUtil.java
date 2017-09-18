/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.beans;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * ATTENTION! DO NOT IMPORT @NotNull AND @Nullable ANNOTATIONS
 * This class is also used on jetbrains web site
 */

public class ConvertUsagesUtil {
  private static final char GROUP_SEPARATOR = ':';
  private static final char GROUPS_SEPARATOR = ';';
  private static final char GROUP_VALUE_SEPARATOR = ',';

  private ConvertUsagesUtil() {
  }


  // @NotNull
  public static <T extends UsageDescriptor> String convertUsages(Map<GroupDescriptor, Set<T>> map) {
    assert map != null;
    final Map<GroupDescriptor, Set<T>> sortedMap = sortDescriptorsByPriority(map);

    StringBuffer buffer = new StringBuffer();
    for (Map.Entry<GroupDescriptor, Set<T>> entry : sortedMap.entrySet()) {
      String value = convertValueMap(entry.getValue());
      if (!StringUtil.isEmptyOrSpaces(value)) {
        buffer.append(entry.getKey().getId());
        buffer.append(GROUP_SEPARATOR);
        buffer.append(value);
        buffer.append(GROUPS_SEPARATOR);
      }
    }

    return buffer.toString();
  }

  //@NotNull
  public static String convertValueMap(Set<? extends UsageDescriptor> descriptors) {
    assert descriptors != null;
    if (descriptors.isEmpty()) return "";
    final StringBuffer buffer = new StringBuffer();
    for (UsageDescriptor usageDescriptor : descriptors) {
      int value = usageDescriptor.getValue();
      if (value != 0) {
        buffer.append(usageDescriptor.getKey());
        buffer.append("=");
        buffer.append(value);
        buffer.append(GROUP_VALUE_SEPARATOR);
      }
    }
    if (buffer.length() == 0) return "";
    buffer.deleteCharAt(buffer.length() - 1);

    return buffer.toString();
  }

  //@NotNull
  public static String cutDataString(String patchStr, int maxSize) {
    assert patchStr != null;
    for (int i = maxSize - 1; i >= 0; i--) {
      final char c = patchStr.charAt(i);
      if (c == GROUPS_SEPARATOR || c == GROUP_VALUE_SEPARATOR) {
        return patchStr.substring(0, i);
      }
    }
    return "";
  }

  //@NotNull
  public static Map<GroupDescriptor, Set<UsageDescriptor>> convertString(String usages) {
    assert usages != null;
    Map<GroupDescriptor, Set<UsageDescriptor>> descriptors = new HashMap<>();
    for (String groupStr : usages.split(Character.toString(GROUPS_SEPARATOR))) {
      if (!isEmptyOrSpaces(groupStr)) {
        final StringPair group = getPair(groupStr, Character.toString(GROUP_SEPARATOR));
        if (group != null) {
          final String groupId = group.first;
          assert groupId != null;
          descriptors.putAll(convertValueString(GroupDescriptor.create(groupId), group.second));
        }
      }
    }
    return descriptors;
  }

  //@NotNull
  public static Map<GroupDescriptor, Set<UsageDescriptor>> convertValueString(GroupDescriptor groupId, String valueData) {
    assert groupId != null;
    final Map<GroupDescriptor, Set<UsageDescriptor>> descriptors = new HashMap<>();
    for (String value : valueData.split(Character.toString(GROUP_VALUE_SEPARATOR))) {
      if (!isEmptyOrSpaces(value)) {
        final StringPair pair = getPair(value, "=");
        if (pair != null) {
          final String count = pair.second;
          if (!isEmptyOrSpaces(count)) {
            try {
              final int i = Integer.parseInt(count);
              if (!descriptors.containsKey(groupId)) {
                descriptors.put(groupId, new LinkedHashSet<>());
              }
              descriptors.get(groupId).add(new UsageDescriptor(pair.first, i));
            }
            catch (NumberFormatException ignored) {
            }
          }
        }
      }
    }

    return descriptors;
  }

  //@Nullable
  public static StringPair getPair(String str, String separator) {
    assert str != null;
    assert separator != null;
    final int i = str.indexOf(separator);
    if (i > 0 && i < str.length() - 1) {
      String key = str.substring(0, i).trim();
      String value = str.substring(i + 1).trim();
      if (!isEmptyOrSpaces(key) && !isEmptyOrSpaces(value)) {
        return new StringPair(key, value);
      }
    }
    return null;
  }

  //@NotNull
  public static <T extends UsageDescriptor> Map<GroupDescriptor, Set<T>> sortDescriptorsByPriority(Map<GroupDescriptor, Set<T>> descriptors) {
    assert descriptors != null;
    final SortedMap<GroupDescriptor, Set<T>> map = new TreeMap<>((g1, g2) -> {
      final int priority = (int)(g2.getPriority() - g1.getPriority());
      return priority == 0 ? g1.getId().compareTo(g2.getId()) : priority;
    });

    map.putAll(descriptors);

    return map;
  }

  /**
   * Escapes descriptor name so it could be used in {@link #assertDescriptorName(String)}
   *
   * @param name name to escape
   * @return escaped name
   */
  @NotNull
  public static String escapeDescriptorName(@NotNull final String name) {
    return name.
      replace(GROUP_SEPARATOR, '_').
      replace(GROUPS_SEPARATOR, '_').
      replace(GROUP_VALUE_SEPARATOR, '_')
      .replace("'", " ")
      .replace("\"", " ");
  }

  private static class StringPair {
    public final String first;
    public final String second;

    public StringPair(String first, String second) {
      this.first = first;
      this.second = second;
    }
  }

  public static boolean isEmptyOrSpaces(final String s) {
    return s == null || s.trim().length() == 0;
  }

  /**
   * @see #escapeDescriptorName(String)
   */
  public static void assertDescriptorName(String key) {
    assert key != null;
    assert key.indexOf(GROUP_SEPARATOR) == -1 : key + " contains invalid chars";
    assert key.indexOf(GROUPS_SEPARATOR) == -1 : key + " contains invalid chars";
    assert key.indexOf(GROUP_VALUE_SEPARATOR) == -1 : key + " contains invalid chars";
    assert !key.contains("=") : key + " contains invalid chars";
    assert !key.contains("'") : key + " contains invalid chars";
    assert !key.contains("\"") : key + " contains invalid chars";
  }

  //@NotNull
  public static String ensureProperKey(/*@NotNull*/ String input) {
    final StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      final char ch = input.charAt(i);
      switch (ch) {
        case GROUP_SEPARATOR:
        case GROUPS_SEPARATOR:
        case GROUP_VALUE_SEPARATOR:
        case '\'':
        case '\"':
        case '=':
          escaped.append(' ');
          break;
        default:
          escaped.append(ch);
          break;
      }
    }
    return escaped.toString();
  }
}
