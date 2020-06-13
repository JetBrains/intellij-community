// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.internal.statistic.StatisticsStringUtil.isEmptyOrSpaces;
import static com.intellij.internal.statistic.StatisticsStringUtil.split;

public final class EventLogBuild implements Comparable<EventLogBuild> {
  public static final int SNAPSHOT_VALUE = Integer.MAX_VALUE;

  private final int[] myComponents;

  public EventLogBuild(int... components) {
    myComponents = components;
  }

  public int[] getComponents() {
    return myComponents.clone();
  }

  @Nullable
  public static EventLogBuild fromString(@Nullable String version) {
    if (version == null || isEmptyOrSpaces(version)) {
      return null;
    }

    String versionWithoutCode = removeProductCode(version);
    int separator = versionWithoutCode.indexOf('.');
    if (separator > 0) {
      List<String> components = split(versionWithoutCode , '.');
      return new EventLogBuild(toIntArray(components));
    }
    return new EventLogBuild(tryParseInt(versionWithoutCode), 0);
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
  public int compareTo(@NotNull EventLogBuild o) {
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
    EventLogBuild number = (EventLogBuild)o;
    return Arrays.equals(myComponents, number.myComponents);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(myComponents);
  }
}