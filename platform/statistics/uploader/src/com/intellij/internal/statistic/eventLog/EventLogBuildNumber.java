// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EventLogBuildNumber implements Comparable<EventLogBuildNumber> {
  public static final int SNAPSHOT_VALUE = Integer.MAX_VALUE;

  @NotNull private final int[] myComponents;

  public EventLogBuildNumber(@NotNull int... components) {
    myComponents = components;
  }

  @NotNull
  public int[] getComponents() {
    return myComponents.clone();
  }

  @Nullable
  public static EventLogBuildNumber fromString(@Nullable String version) {
    if (StatisticsEventLogUtil.isEmptyOrSpaces(version)) {
      return null;
    }

    String versionWithoutCode = removeProductCode(version);
    int separator = versionWithoutCode.indexOf('.');
    if (separator > 0) {
      List<String> components = split(versionWithoutCode);
      return new EventLogBuildNumber(toIntArray(components));
    }
    return new EventLogBuildNumber(tryParseInt(versionWithoutCode), 0);
  }

  private static int[] toIntArray(List<String> components) {
    int componentsSize = components.size();
    int size = componentsSize != 1 ? componentsSize : 2;
    int[] array = new int[size];
    for (int i = 0; i < componentsSize; i++) {
      String component = components.get(i);
      array[i] = tryParseInt(component);
    }

    if (componentsSize < size) {
      array[componentsSize] = 0;
    }
    return array;
  }

  private static List<String> split(String text) {
    List<String> result = new ArrayList<>();
    int pos = 0;
    int index = text.indexOf('.', pos);
    while (index > 0) {
      final int nextPos = index + 1;
      String token = text.substring(pos, index);
      if (token.length() != 0) {
        result.add(token);
      }
      pos = nextPos;
      index = text.indexOf('.', pos);
    }

    if (pos < text.length()) {
      result.add(text.substring(pos));
    }
    return result;
  }

  @NotNull
  private static String removeProductCode(@NotNull String version) {
    String code = version;
    int productSeparator = code.indexOf('-');
    if (productSeparator > 0) {
      code = code.substring(productSeparator + 1);
    }
    return code;
  }

  private static int tryParseInt(@NotNull String version) {
    try {
      return Integer.parseInt(version);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public int compareTo(@NotNull EventLogBuildNumber o) {
    int[] c1 = myComponents;
    int[] c2 = o.myComponents;

    for (int i = 0; i < Math.min(c1.length, c2.length); i++) {
      if (c1[i] == c2[i] && c1[i] == SNAPSHOT_VALUE) return 0;
      if (c1[i] == SNAPSHOT_VALUE) return 1;
      if (c2[i] == SNAPSHOT_VALUE) return -1;
      int result = c1[i] - c2[i];
      if (result != 0) return result;
    }

    return c1.length - c2.length;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EventLogBuildNumber number = (EventLogBuildNumber)o;
    return Arrays.equals(myComponents, number.myComponents);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(myComponents);
  }
}