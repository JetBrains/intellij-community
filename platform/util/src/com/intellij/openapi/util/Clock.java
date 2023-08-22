// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Date;

public final class Clock {
  private static long ourTime = -1;

  public static long getTime() {
    if (ourTime != -1) return ourTime;
    return System.currentTimeMillis();
  }

  @TestOnly
  public static void setTime(long time) {
    ourTime = time;
  }

  @TestOnly
  public static void setTime(@NotNull Date date) {
    setTime(date.getTime());
  }

  @TestOnly
  public static void setTime(int year, int month, int day) {
    setTime(year, month, day, 0, 0);
  }

  @TestOnly
  public static void setTime(int year, int month, int day, int hours, int minutes) {
    setTime(year, month, day, hours, minutes, 0);
  }

  @TestOnly
  public static void setTime(int year, int month, int day, int hours, int minutes, int seconds) {
    setTime(new Date(year - 1900, month, day, hours, minutes, seconds));
  }

  @TestOnly
  public static void reset() {
    ourTime = -1;
  }
}
